package com.lsama.measurephoto.editor

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.model.Point
import com.lsama.measurephoto.render.Renderer
import kotlin.math.*

class PhotoCanvas(context: Context): View(context) {
    var bitmap: Bitmap?=null
    var document=Document()
    var selected: String?=null
    var drawMode=true
    var pen=Pen()
    var onCommit: (Document)->Unit={}
    var onSelect: (String?)->Unit={}
    var onDimension: (String)->Unit={}
    var onLineCreated: (String)->Unit={}
    var fitToken=0
    private var lastFit=-1
    private val dp=resources.displayMetrics.density
    private var scale=1f;private var tx=0f;private var ty=0f;private var base=1f
    private var fittedWidth=0;private var fittedHeight=0
    private var down=Point(0f,0f);private var previous=down
    private var start: Point?=null;private var end: Point?=null
    private var startId: String?=null;private var snapId: String?=null
    private var hitVertex: String?=null;private var hitLabel: String?=null;private var hitLine: String?=null
    private var original=Document();private var draft: Document?=null
    private var moved=false;private var longPressed=false;private var downTime=0L
    private val gate=GestureGate();private var span=0f;private var center=Point(0f,0f)
    private val longPress=Runnable { if(!moved && !gate.blocked && hitLine!=null) {longPressed=true;selected=hitLine;onSelect(hitLine);performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);invalidate()} }
    private fun transform()=Transform(bitmap?.width?.toFloat()?:1f,bitmap?.height?.toFloat()?:1f,scale,tx,ty)
    fun fit() { val b=bitmap?:return; if(width==0||height==0) return; base=min(width.toFloat()/b.width,height.toFloat()/b.height)*0.94f;scale=base;tx=(width-b.width*scale)/2;ty=(height-b.height*scale)/2;lastFit=fitToken;fittedWidth=width;fittedHeight=height;invalidate() }
    override fun onSizeChanged(w: Int,h: Int,ow: Int,oh: Int) {super.onSizeChanged(w,h,ow,oh);fit()}
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas);val b=bitmap?:return
        if(lastFit!=fitToken || fittedWidth!=width || fittedHeight!=height) fit()
        val doc=draft?:document
        canvas.save();canvas.translate(tx,ty);canvas.scale(scale,scale);canvas.drawBitmap(b,0f,0f,Paint(Paint.FILTER_BITMAP_FLAG));Renderer.annotations(canvas,doc,b.width.toFloat(),b.height.toFloat());canvas.restore()
        val t=transform();val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {strokeWidth=2*dp;color=0xFF2F655C.toInt();style=Paint.Style.STROKE}
        doc.segments.find {it.id==selected}?.let {s->listOf(doc.vertex(s.start),doc.vertex(s.end)).forEach {v-> val q=t.screen(v.point);p.style=Paint.Style.FILL;p.color=Color.WHITE;canvas.drawCircle(q.x,q.y,6*dp,p);p.style=Paint.Style.STROKE;p.color=0xFF2F655C.toInt();canvas.drawCircle(q.x,q.y,6*dp,p) } }
        start?.let {a->end?.let {b2->val q=t.screen(a);val r=t.screen(b2);p.color=pen.color;p.strokeWidth=pen.width*min(b.width,b.height)/360*scale;p.strokeCap=Paint.Cap.ROUND;canvas.drawLine(q.x,q.y,r.x,r.y,p)}}
        snapId?.let {id->doc.vertices.find {it.id==id}?.let {val q=t.screen(it.point);p.color=0xFF2F655C.toInt();p.strokeWidth=2*dp;canvas.drawCircle(q.x,q.y,12*dp,p)}}
    }
    private fun inside(p: Point)=p.x in 0f..1f && p.y in 0f..1f
    private fun dragLabel(s: Segment,p: Point,t: Transform): Document {
        val r=Renderer.label(original,s,t.width,t.height) ?: return original
        val a=original.vertex(s.start);val b=original.vertex(s.end)
        return original.update(s.id) {it.copy(
            offsetX=r.centerX()/t.width-(a.x+b.x)/2+(p.x-down.x)/(t.width*scale),
            offsetY=r.centerY()/t.height-(a.y+b.y)/2+(p.y-down.y)/(t.height*scale))}
    }
    private fun snap(p: Point,exclude: String?=null): Vertex? {
        val t=transform();val held=document.vertices.find {it.id==snapId && it.id!=exclude}
        return if(held!=null && t.screen(held.point).distance(p)<24*dp) held else t.snap(document,p,16*dp,exclude)
    }
    override fun performClick(): Boolean {super.performClick();return true}
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if(bitmap==null) return true
        parent.requestDisallowInterceptTouchEvent(true)
        val p=Point(e.x,e.y);val t=transform()
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN->{
                gate.allUp();original=document;draft=null;down=p;previous=p;moved=false;longPressed=false;downTime=e.eventTime
                hitVertex=null;hitLabel=null;hitLine=null;start=null;end=null;startId=null;snapId=null
                val seg=document.segments.find {it.id==selected}
                if(!drawMode && seg!=null) hitVertex=listOf(document.vertex(seg.start),document.vertex(seg.end)).minByOrNull {t.screen(it.point).distance(p)}?.takeIf {t.screen(it.point).distance(p)<=24*dp}?.id
                hitLabel=document.segments.asReversed().firstOrNull {s->Renderer.label(document,s,t.width,t.height)?.let {r->val ip=t.inverse(p);r.contains(ip.x*t.width,ip.y*t.height)}==true}?.id
                // A visible number owns its pixels. The enlarged endpoint hit area must not
                // steal a label drag; direct hits on the visible endpoint handle still win.
                if(hitLabel!=null && hitVertex!=null && t.screen(document.vertex(hitVertex!!).point).distance(p)>8*dp) hitVertex=null
                hitLine=hitLabel?:document.segments.minByOrNull {s->lineDistance(p,t.screen(document.vertex(s.start).point),t.screen(document.vertex(s.end).point))}?.takeIf {s->lineDistance(p,t.screen(document.vertex(s.start).point),t.screen(document.vertex(s.end).point))<12*dp}?.id
                if(drawMode && inside(t.inverse(p))) {val v=snap(p);start=v?.point?:t.inverse(p);startId=v?.id}
                if(drawMode || (hitVertex==null && hitLabel==null)) postDelayed(longPress,ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_POINTER_DOWN->{
                gate.secondPointer();removeCallbacks(longPress);start=null;end=null;draft=null;snapId=null
                if(e.pointerCount>=2) {span=hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));center=Point((e.getX(0)+e.getX(1))/2,(e.getY(0)+e.getY(1))/2)}
            }
            MotionEvent.ACTION_MOVE->{
                if(gate.blocked) {
                    if(e.pointerCount>=2) {val nextSpan=hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));val nextCenter=Point((e.getX(0)+e.getX(1))/2,(e.getY(0)+e.getY(1))/2);val newScale=(scale*nextSpan/max(1f,span)).coerceIn(base,base*8);val ratio=newScale/scale;tx=nextCenter.x-(center.x-tx)*ratio;ty=nextCenter.y-(center.y-ty)*ratio;scale=newScale;span=nextSpan;center=nextCenter}
                } else {
                    if(p.distance(down)>ViewConfiguration.get(context).scaledTouchSlop) {moved=true;removeCallbacks(longPress)}
                    if(moved && !longPressed) {
                        if(drawMode) { if(start!=null) {val v=snap(p);snapId=v?.id;end=v?.point?:t.inverse(p).bounded()} }
                        else if(hitVertex!=null) {val v=snap(p,hitVertex);snapId=v?.id;draft=original.move(hitVertex!!,v?.point?:t.inverse(p).bounded())}
                        else if(hitLabel!=null) {val s=original.segments.first {it.id==hitLabel};draft=dragLabel(s,p,t)}
                        else {tx+=p.x-previous.x;ty+=p.y-previous.y}
                    }
                }
                previous=p
            }
            MotionEvent.ACTION_UP->{
                removeCallbacks(longPress)
                if(!gate.blocked && !longPressed) {
                    if(moved && drawMode && start!=null) {val v=snap(p);snapId=v?.id;end=v?.point?:t.inverse(p).bounded()}
                    if(moved && !drawMode && hitVertex!=null) {val v=snap(p,hitVertex);snapId=v?.id;draft=original.move(hitVertex!!,v?.point?:t.inverse(p).bounded())}
                    if(moved && !drawMode && hitVertex==null && hitLabel!=null) {val s=original.segments.first {it.id==hitLabel};draft=dragLabel(s,p,t)}
                    if(!moved) {performClick();onSelect(hitLine);if(hitLine!=null) onDimension(hitLine!!)}
                    else if(drawMode && start!=null && end!=null && t.screen(start!!).distance(t.screen(end!!))>=8*dp) {val next=document.add(start!!,end!!,startId,snapId,pen);if(next!=document) {onCommit(next);onLineCreated(next.segments.last().id)}}
                    else if(!drawMode && draft!=null) {var next=draft!!;if(hitVertex!=null && snapId!=null) {val merged=next.merge(hitVertex!!,snapId!!);next=if(merged==next) original else merged};if(next!=original) onCommit(next)}
                }
                start=null;end=null;draft=null;snapId=null;gate.allUp()
            }
            MotionEvent.ACTION_CANCEL->{removeCallbacks(longPress);start=null;end=null;draft=null;snapId=null;gate.allUp()}
        }
        invalidate();return true
    }
}
