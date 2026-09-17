package com.lsama.measurephoto

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.export.*
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.model.Point
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Opt-in by test class when a device has space for the 100-photo workload. */
class LoadTest {
    @Test fun hundredTwelveMegapixelPhotos() = runBlocking {
        org.junit.Assume.assumeTrue(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("load")=="true")
        val context=ApplicationProvider.getApplicationContext<Context>();val store=Store(context)
        val project=Project(name="负载测试-${uid().take(6)}");store.dao.project(project)
        val source=File(context.cacheDir,"12mp-load.jpg")
        val b=Bitmap.createBitmap(4000,3000,Bitmap.Config.ARGB_8888);val c=Canvas(b);val p=Paint()
        for(y in 0 until 3000 step 40) for(x in 0 until 4000 step 40) {p.color=Color.rgb((x*17+y)%256,(y*11+x)%256,(x+y*7)%256);c.drawRect(x.toFloat(),y.toFloat(),x+40f,y+40f,p)}
        source.outputStream().use {b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()
        var doc=Document()
        for(i in 0 until 100) {val row=i/10;val col=i%10;doc=doc.add(Point(.02f+col*.095f,.02f+row*.095f),Point(.08f+col*.095f,.07f+row*.095f),null,null,Pen());val last=doc.segments.last();doc=doc.update(last.id){it.copy(dimension="${1200+i}")}}
        val items=mutableListOf<ExportItem>();val start=SystemClock.elapsedRealtime();var peak=0
        val sampler=launch(Dispatchers.Default) {while(isActive) {val memory=Debug.MemoryInfo();Debug.getMemoryInfo(memory);peak=maxOf(peak,memory.totalPss);delay(200)}}
        val pdf=File(context.cacheDir,"load-100.pdf")
        val job=ExportJob(project=project.name,items=items,album=true,pdf=true,pdfUri=Uri.fromFile(pdf).toString())
        var failure: String?=null
        try {
            repeat(100) {i->val photo=store.addPhoto(project.id,Uri.fromFile(source),i);val saved=store.save(photo.copy(roomName="测试房间 ${i+1}",note="十二百万像素原图，100 条可编辑尺寸线。"),doc);items+=ExportItem(saved,i+1,doc)}
            ExportEngine(store).run(job){}
            assertEquals("已完成",job.state);assertEquals(100,job.items.count {it.uri!=null});assertTrue(job.pdfDone)
        } catch(t: Throwable) {failure=t.toString();throw t}
        finally {
            sampler.cancelAndJoin()
            val output=File(context.getExternalFilesDir(null),"test-artifacts").apply {mkdirs()}
            File(output,"load-test.txt").writeText("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, API ${android.os.Build.VERSION.SDK_INT}\n100 x 4000x3000 photos, 100 segments each\nElapsed ms: ${SystemClock.elapsedRealtime()-start}\nPeak process PSS KiB: $peak\nAlbum completed: ${job.items.count {it.uri!=null}}\nPDF bytes: ${pdf.length()}\nState: ${job.state}\nFailure: $failure\n")
            job.items.mapNotNull {it.uri}.forEach {runCatching {context.contentResolver.delete(Uri.parse(it),null,null)}}
            store.dao.deleteJob(job.id);store.deleteProject(project);source.delete();pdf.delete();store.db.close()
        }
    }
}
