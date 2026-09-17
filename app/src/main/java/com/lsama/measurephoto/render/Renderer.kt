package com.lsama.measurephoto.render

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.lsama.measurephoto.model.*
import kotlin.math.*

object Photos {
    fun info(path: String): Triple<Int,Int,Int> {
        val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(path,opts)
        require(opts.outWidth>0 && opts.outHeight>0) { "图片已损坏或格式不支持" }
        val orientation=ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION,1)
        return if(orientation in 5..8) Triple(opts.outHeight,opts.outWidth,orientation) else Triple(opts.outWidth,opts.outHeight,orientation)
    }
    fun matrix(orientation: Int)=Matrix().apply {
        when(orientation) {
            2->setScale(-1f,1f);3->setRotate(180f);4->{setRotate(180f);postScale(-1f,1f)}
            5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f)
            7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)
        }
    }
    fun decode(path: String,maxEdge: Int): Bitmap {
        val (w,h,o)=info(path)
        var sample=1;while(max(w,h)/sample>maxEdge*2) sample*=2
        val raw=BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize=sample;inMutable=true;inPreferredConfig=Bitmap.Config.ARGB_8888 }) ?: error("无法解码照片")
        val oriented=Bitmap.createBitmap(raw,0,0,raw.width,raw.height,matrix(o),true)
        if(oriented!==raw) raw.recycle()
        val scale=min(1f,maxEdge.toFloat()/max(oriented.width,oriented.height))
        val resized=if(scale<1) Bitmap.createScaledBitmap(oriented,max(1,(oriented.width*scale).roundToInt()),max(1,(oriented.height*scale).roundToInt()),true) else oriented
        if(resized!==oriented) oriented.recycle()
        if(resized.isMutable) return resized
        return resized.copy(Bitmap.Config.ARGB_8888,true).also { resized.recycle() }
    }
}
object Renderer {
    private fun textPaint(size: Float)=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=0xFF232725.toInt();textSize=size;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)}
    fun label(doc: Document,s: Segment,w: Float,h: Float): RectF? {
        val text=s.dimension ?: return null
        val a=doc.vertex(s.start);val b=doc.vertex(s.end);val unit=min(w,h)/360f
        val p=textPaint(s.pen.font*unit)
        // Scale very long labels to fit the image; same geometry for screen, thumbnail and exports.
        val width=min(w,p.measureText(text)+12*unit);val height=min(h,(p.fontMetrics.descent-p.fontMetrics.ascent)+6*unit)
        val offset=if(s.offsetX!=0f || s.offsetY!=0f) com.lsama.measurephoto.model.Point(s.offsetX*w,s.offsetY*h) else null
        val bounds=placeLabel(com.lsama.measurephoto.model.Point(a.x*w,a.y*h),com.lsama.measurephoto.model.Point(b.x*w,b.y*h),w,h,width,height,(s.pen.width/2+4)*unit,offset)
        return RectF(bounds.left,bounds.top,bounds.right,bounds.bottom)
    }
    fun annotations(c: Canvas,doc: Document,w: Float,h: Float) {
        val unit=min(w,h)/360f
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        c.save();c.clipRect(0f,0f,w,h)
        doc.segments.forEach { s->val a=doc.vertex(s.start);val b=doc.vertex(s.end);p.color=s.pen.color;p.strokeWidth=s.pen.width*unit;c.drawLine(a.x*w,a.y*h,b.x*w,b.y*h,p) }
        doc.segments.forEach { s->label(doc,s,w,h)?.let { r->
            val text=s.dimension!!;val t=textPaint(s.pen.font*unit)
            if(t.measureText(text)>r.width()-8*unit) t.textSize*=max(0.1f,(r.width()-8*unit)/t.measureText(text))
            val x=r.centerX()-t.measureText(text)/2;val y=r.centerY()-(t.fontMetrics.ascent+t.fontMetrics.descent)/2
            // Outline follows the glyphs only: transparent counters and no filled label rectangle.
            t.style=Paint.Style.STROKE;t.strokeWidth=2*unit;t.strokeJoin=Paint.Join.ROUND;t.color=Color.WHITE
            c.drawText(text,x,y,t)
            t.style=Paint.Style.FILL;t.color=0xFF232725.toInt();c.drawText(text,x,y,t)
        } }
        c.restore()
    }
    fun text(text: String,width: Int,size: Float,color: Int=0xFF232725.toInt()): StaticLayout {
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;textSize=size;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        return StaticLayout.Builder.obtain(text,0,text.length,paint,max(1,width)).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(size*0.25f,1f).setIncludePad(false).build()
    }
}
