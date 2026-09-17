package com.lsama.measurephoto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.lsama.measurephoto.editor.PhotoCanvas
import com.lsama.measurephoto.model.Document
import com.lsama.measurephoto.model.Pen
import com.lsama.measurephoto.model.Point
import com.lsama.measurephoto.render.Renderer
import org.junit.Assert.*
import org.junit.Test

class CanvasInteractionTest {
    private fun shortLine()=Document().add(Point(.4f,.6f),Point(.43f,.6f),null,null,Pen()).let {d->d.update(d.segments.single().id){it.copy(dimension="50")}}

    @Test fun draggingShortLineNumberDoesNotMoveEndpointsOrJump() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=ApplicationProvider.getApplicationContext<android.content.Context>()
            val pixels=(360*context.resources.displayMetrics.density).toInt()
            val parent=FrameLayout(context);val view=PhotoCanvas(context);parent.addView(view)
            val bitmap=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888)
            try {
                val before=shortLine();view.bitmap=bitmap;view.document=before;view.selected=before.segments.single().id;view.drawMode=false
                var result=before;view.onCommit={result=it;view.document=it}
                parent.layout(0,0,pixels,pixels);view.layout(0,0,pixels,pixels);view.fit()
                val scale=pixels/360f*.94f;val margin=(pixels-360*scale)/2
                val r=Renderer.label(before,before.segments.single(),360f,360f)!!
                val x=margin+r.centerX()*scale;val y=margin+r.centerY()*scale
                val shift=40*context.resources.displayMetrics.density;val now=SystemClock.uptimeMillis()
                for((i,action) in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).withIndex()) {
                    val e=MotionEvent.obtain(now,now+i*100L,action,x,y-shift*i/2,0);view.dispatchTouchEvent(e);e.recycle()
                }
                assertEquals("Dragging the number must preserve both endpoints",before.vertices,result.vertices)
                assertEquals("50",result.segments.single().dimension)
                val after=Renderer.label(result,result.segments.single(),360f,360f)!!
                assertEquals(r.centerX(),after.centerX(),.01f)
                assertEquals(r.centerY()-shift/scale,after.centerY(),.01f)
            } finally {bitmap.recycle()}
        }
    }

    @Test fun shortLineAndLabelHaveNoFilledRectangleOnBrightOrDarkPhotos() {
        val d=shortLine();val s=d.segments.single();val r=Renderer.label(d,s,360f,360f)!!
        for(background in listOf(Color.rgb(32,40,48),Color.rgb(218,222,226))) {
            val b=Bitmap.createBitmap(360,360,Bitmap.Config.ARGB_8888)
            try {
                val c=Canvas(b);c.drawColor(background);Renderer.annotations(c,d,360f,360f)
                assertEquals("Label corner must remain the photo",background,b.getPixel(r.left.toInt()+1,r.top.toInt()+1))
                assertEquals("The entire short line remains visible",s.pen.color,b.getPixel(149,216))
            } finally {b.recycle()}
        }
    }
}
