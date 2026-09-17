package com.lsama.measurephoto

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.export.*
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.model.Point
import com.lsama.measurephoto.render.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class) class DeviceTests {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun databaseRenameAndReopenPreservesDocument() = runBlocking {
        val name="test-${uid()}.db"
        var db=Room.databaseBuilder(context,Database::class.java,name).build()
        val p=Project(name="测试厨房");db.dao().project(p)
        val doc=Document().add(Point(.1f,.2f),Point(.8f,.8f),null,null,Pen()).let {it.update(it.segments[0].id){s->s.copy(dimension="1250.5")}}
        val gson=com.google.gson.Gson();val photo=Photo(projectId=p.id,originalPath="test",thumbnailPath="test",orientedWidth=400,orientedHeight=300,document=gson.toJson(doc))
        db.dao().save(photo);db.dao().project(p.copy(name="改名"));assertEquals(1,db.dao().allPhotos().size);db.close()
        db=Room.databaseBuilder(context,Database::class.java,name).build()
        assertEquals(doc,gson.fromJson(db.dao().allPhotos()[0].document,Document::class.java));db.close();context.deleteDatabase(name);Unit
    }
    @Test fun exifEightOrientationsAndLabelBounds() {
        for(o in 1..8) {
            val src=Bitmap.createBitmap(120,80,Bitmap.Config.ARGB_8888);val c=Canvas(src);c.drawColor(Color.WHITE)
            val p=Paint().apply {color=Color.RED};c.drawRect(0f,0f,40f,30f,p);p.color=Color.BLUE;c.drawRect(80f,50f,120f,80f,p)
            val file=File(context.cacheDir,"exif-$o.jpg");file.outputStream().use {src.compress(Bitmap.CompressFormat.JPEG,100,it)};src.recycle()
            ExifInterface(file).apply {setAttribute(ExifInterface.TAG_ORIENTATION,o.toString());saveAttributes()}
            val b=Photos.decode(file.path,1000);assertEquals(if(o in 5..8) 80 else 120,b.width)
            // Independently known red-corner mapping for each EXIF orientation.
            val redCorner=when(o) {1,5->Pair(5,5);2,6->Pair(b.width-6,5);3,7->Pair(b.width-6,b.height-6);else->Pair(5,b.height-6)}
            val color=b.getPixel(redCorner.first,redCorner.second);assertTrue("EXIF $o",Color.red(color)>200 && Color.blue(color)<80)
            var doc=Document().add(Point(0f,0f),Point(.05f,.05f),null,null,Pen());doc=doc.update(doc.segments[0].id){it.copy(dimension="99999999.99",offsetX= -1f)}
            val r=Renderer.label(doc,doc.segments[0],b.width.toFloat(),b.height.toFloat())!!;assertTrue(r.left>=0 && r.right<=b.width && r.top>=0)
            Renderer.annotations(Canvas(b),doc,b.width.toFloat(),b.height.toFloat());b.recycle();file.delete()
        }
    }
    @Test fun chineseNotePdfAndAlbumRetry() = runBlocking {
        val store=Store(context);val project=Project(name="中文导出验证");store.dao.project(project)
        val file=File(context.cacheDir,"export-test.jpg");val b=Bitmap.createBitmap(1200,800,Bitmap.Config.ARGB_8888);Canvas(b).drawColor(Color.LTGRAY);file.outputStream().use {b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()
        var p=store.addPhoto(project.id,Uri.fromFile(file),0)
        var d=Document().add(Point(.1f,.1f),Point(.8f,.8f),null,null,Pen());d=d.update(d.segments[0].id){it.copy(dimension="2500")}
        p=store.save(p.copy(roomName="厨房",note="右侧预留检修空间，注意燃气管。".repeat(100).take(1000)),d)
        val pdf=File(context.cacheDir,"量尺测试.pdf");val job=ExportJob(project=project.name,items=listOf(ExportItem(p,1,d)),album=true,pdf=true,pdfUri=Uri.fromFile(pdf).toString())
        val engine=ExportEngine(store);engine.run(job){}
        assertEquals("已完成",job.state);assertTrue(pdf.length()>1000);val uri=job.items[0].uri;assertNotNull(uri)
        engine.run(job){};assertEquals(uri,job.items[0].uri)
        android.os.ParcelFileDescriptor.open(pdf,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use {fd->android.graphics.pdf.PdfRenderer(fd).use {renderer->assertTrue(renderer.pageCount>=2)}}
        val artifacts=File(context.getExternalFilesDir(null),"test-artifacts").apply {mkdirs()}
        pdf.copyTo(File(artifacts,"中文长备注-导出验证.pdf"),overwrite=true)
        context.contentResolver.openInputStream(Uri.parse(uri))!!.use {input->File(artifacts,"中文长备注-导出验证.jpg").outputStream().use {input.copyTo(it)}}
        context.contentResolver.delete(Uri.parse(uri),null,null);store.dao.deleteJob(job.id);store.deleteProject(project);file.delete();pdf.delete();store.db.close()
    }
}
