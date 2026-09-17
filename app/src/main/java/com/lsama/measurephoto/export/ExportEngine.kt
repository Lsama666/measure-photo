package com.lsama.measurephoto.export

import android.content.ContentValues
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.render.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

data class ExportItem(val photo: Photo,val number: Int,val doc: Document,var uri: String?=null,var error: String?=null)
data class ExportJob(val id: String=uid(),val project: String,val items: List<ExportItem>,val album: Boolean,val pdf: Boolean,
    var pdfUri: String?=null,var pdfDone: Boolean=false,var state: String="准备中",var error: String?=null,
    val timestamp: String=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")))
fun safeName(s: String)=s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"),"_").trim().take(50).ifBlank { "未命名" }
fun imageName(job: ExportJob,item: ExportItem)="${safeName(job.project)}_${item.number.toString().padStart(3,'0')}${if(item.photo.roomName.isBlank()) "" else "_"+safeName(item.photo.roomName)}_${job.timestamp}_${uid().take(8)}.jpg"
class ExportEngine(private val store: Store) {
    private val resolver=store.context.contentResolver
    suspend fun persist(job: ExportJob) { store.dao.job(ExportRecord(job.id,store.gson.toJson(job))) }
    suspend fun recover() {
        store.dao.allJobs().forEach { record->val j=store.gson.fromJson(record.json,ExportJob::class.java)
            if(j.state=="导出中" || j.state=="准备中") {j.state="已中断";persist(j)} }
    }
    private fun info(job: ExportJob,item: ExportItem)="${job.project}\n照片 ${item.number}${if(item.photo.roomName.isBlank()) "" else " · ${item.photo.roomName}"}　单位：mm"
    private fun drawText(c: Canvas,text: String,x: Float,y: Float,width: Int,size: Float): Int {val layout=Renderer.text(text,width,size);c.save();c.translate(x,y);layout.draw(c);c.restore();return layout.height}
    private fun album(job: ExportJob,item: ExportItem): Uri {
        val bmp=Photos.decode(item.photo.originalPath,4096)
        var result: Bitmap?=null;var target: Uri?=null
        try {
            val unit=max(1f,bmp.width/600f);val pad=20*unit;val width=max(1,(bmp.width-2*pad).toInt())
            val description=info(job,item)+(if(item.photo.note.isBlank()) "" else "\n\n"+item.photo.note)
            val layout=Renderer.text(description,width,14*unit)
            result=Bitmap.createBitmap(bmp.width,bmp.height+(pad*2).toInt()+layout.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(result);canvas.drawColor(Color.WHITE);canvas.drawBitmap(bmp,0f,0f,null)
            Renderer.annotations(canvas,item.doc,bmp.width.toFloat(),bmp.height.toFloat())
            canvas.save();canvas.translate(pad,bmp.height+pad);layout.draw(canvas);canvas.restore()
            target=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME,imageName(job,item));put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/现场量尺/${safeName(job.project)}/");put(MediaStore.Images.Media.IS_PENDING,1)
            }) ?: error("无法创建相册记录")
            resolver.openOutputStream(target)?.use { check(result.compress(Bitmap.CompressFormat.JPEG,95,it)) { "JPEG 写入失败" } } ?: error("无法打开相册输出")
            check(resolver.update(target,ContentValues().apply {put(MediaStore.Images.Media.IS_PENDING,0)},null,null)==1) {"媒体发布失败"}
            return target
        } catch(t: Throwable) {target?.let { runCatching {resolver.delete(it,null,null)} };throw t}
        finally {bmp.recycle();result?.recycle()}
    }
    private suspend fun pdf(job: ExportJob) {
        val file=File(store.context.cacheDir,"export-${job.id}.pdf")
        val pdf=PdfDocument();var pageNumber=0
        fun footer(page: PdfDocument.Page,w: Int,h: Int) {drawText(page.canvas,"${++pageNumber}",34f,h-25f,w-68,9f);pdf.finishPage(page)}
        try {
            for(item in job.items) {
                currentCoroutineContext().ensureActive()
                // PdfDocument retains page bitmaps until writeTo, even after recycle().
                // Bound their total raster area; annotations remain vector at every batch size.
                val aspect=min(item.photo.orientedWidth,item.photo.orientedHeight).toDouble()/max(item.photo.orientedWidth,item.photo.orientedHeight)
                val maxEdge=sqrt(48_000_000.0/max(1,job.items.size)/aspect).toInt().coerceIn(1,2400)
                val photo=Photos.decode(item.photo.originalPath,maxEdge)
                try {
                    val landscape=photo.width>photo.height;val w=if(landscape) 842 else 595;val h=if(landscape) 595 else 842
                    val page=pdf.startPage(PdfDocument.PageInfo.Builder(w,h,pageNumber+1).create());val c=page.canvas
                    val headerHeight=drawText(c,info(job,item),34f,30f,w-68,11f)
                    val top=30f+headerHeight+16f
                    val note=item.photo.note
                    val noteLayout=Renderer.text(note,w-68,11f)
                    val reserve=if(note.isBlank()) 0 else min(noteLayout.height,95)+16
                    val areaH=h-40f-top-reserve
                    val factor=min((w-68f)/photo.width,areaH/photo.height)
                    val pw=photo.width*factor;val ph=photo.height*factor;val left=(w-pw)/2
                    c.save();c.translate(left,top);c.scale(factor,factor);c.drawBitmap(photo,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG));Renderer.annotations(c,item.doc,photo.width.toFloat(),photo.height.toFloat());c.restore()
                    var consumed=0
                    if(note.isNotBlank()) {
                        val y=top+ph+12;val available=(h-40-y).toInt();var lines=0
                        while(lines<noteLayout.lineCount && noteLayout.getLineBottom(lines)<=available) lines++
                        consumed=if(lines>0) noteLayout.getLineEnd(lines-1) else 0
                        if(consumed>0) drawText(c,note.substring(0,consumed),34f,y,w-68,11f)
                    }
                    footer(page,w,h)
                    while(consumed<note.length) {
                        currentCoroutineContext().ensureActive()
                        val continuation=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber+1).create())
                        val hh=drawText(continuation.canvas,info(job,item)+" · 备注续页",34f,30f,527,11f)
                        val rest=note.substring(consumed);val layout=Renderer.text(rest,527,11f);var lines=0
                        while(lines<layout.lineCount && layout.getLineBottom(lines)<842-80-hh) lines++
                        check(lines>0);val end=layout.getLineEnd(lines-1)
                        drawText(continuation.canvas,rest.substring(0,end),34f,hh+46f,527,11f);consumed+=end;footer(continuation,595,842)
                    }
                } finally {photo.recycle()}
            }
            file.outputStream().use {pdf.writeTo(it)}
            currentCoroutineContext().ensureActive()
            val uri=Uri.parse(job.pdfUri ?: error("尚未选择 PDF 保存位置"))
            try {resolver.openOutputStream(uri,"wt")?.use {out->file.inputStream().use {it.copyTo(out)} } ?: error("PDF 目标无法写入")}
            catch(t: Throwable) {runCatching {DocumentsContract.deleteDocument(resolver,uri)};throw t}
        } finally {pdf.close();file.delete()}
    }
    suspend fun run(job: ExportJob,progress: (String)->Unit) = withContext(Dispatchers.IO) {
        job.state="导出中";job.error=null;persist(job)
        try {
            if(job.album) for((index,item) in job.items.withIndex()) {
                ensureActive()
                if(item.uri==null) {
                    try {item.uri=album(job,item).toString();item.error=null}
                    catch(t: Exception) {item.error=t.message ?: "图片导出失败"}
                    // Record each completed URI before moving on; retries keep these outputs.
                    withContext(NonCancellable) {persist(job)}
                }
                progress("相册 ${index+1} / ${job.items.size}")
            }
            if(job.pdf && !job.pdfDone) {progress("正在汇总 PDF");pdf(job);job.pdfDone=true;persist(job)}
            job.state=if(job.items.any {it.error!=null}) "部分失败" else "已完成"
        } catch(t: CancellationException) {job.state="已取消";throw t}
        catch(t: Throwable) {job.state="失败";job.error=t.message ?: "导出失败"}
        finally {withContext(NonCancellable) {persist(job)}}
    }
}
