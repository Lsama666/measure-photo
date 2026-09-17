package com.lsama.measurephoto

import android.graphics.*
import android.net.Uri
import android.os.SystemClock
import android.view.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.editor.PhotoCanvas
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Activity, Compose UI, Canvas MotionEvents and Room; no fake production fixtures. */
class EditorFlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,5000)
        val directory=File(context.getExternalFilesDir(null),"test-artifacts").apply {mkdirs()}
        val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(directory,"$name.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
    }
    private fun canvas(v: View): PhotoCanvas? {if(v is PhotoCanvas)return v;if(v is ViewGroup)for(i in 0 until v.childCount){val c=canvas(v.getChildAt(i));if(c!=null)return c};return null}
    @Test fun drawEditUndoReopen() = runBlocking {
        val store=Store(context);val oldAuto=store.autoDimension();store.autoDimension(true)
        val project=Project(name="验收测试·厨房");store.dao.project(project)
        val file=File(context.cacheDir,"ui-fixture.jpg")
        val b=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888)
        val c=Canvas(b);c.drawColor(Color.rgb(221,216,204));val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(155,169,158)}
        c.drawRect(250f,200f,1350f,950f,p);p.color=Color.rgb(240,239,235);c.drawRect(600f,300f,1100f,650f,p)
        file.outputStream().use {b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()
        val photo=store.addPhoto(project.id,Uri.fromFile(file),0)
        val scenario=ActivityScenario.launch(MainActivity::class.java)
        try {
            compose.waitUntil(15000) {compose.onAllNodesWithText(project.name).fetchSemanticsNodes().isNotEmpty()}
            screenshot("01-projects")
            compose.onNodeWithText(project.name).performClick()
            compose.waitForIdle();screenshot("02-photos")
            compose.onNodeWithText("01   现场照片").performClick()
            compose.waitUntil(15000) {var loaded=false;scenario.onActivity {loaded=canvas(it.window.decorView)?.bitmap!=null};loaded}
            scenario.onActivity {a->val v=canvas(a.window.decorView)!!;val now=SystemClock.uptimeMillis()
                fun event(action: Int,x: Float,y: Float,time: Long) {val e=MotionEvent.obtain(now,time,action,x,y,0);v.dispatchTouchEvent(e);e.recycle()}
                event(MotionEvent.ACTION_DOWN,v.width*.25f,v.height*.35f,now)
                event(MotionEvent.ACTION_MOVE,v.width*.65f,v.height*.4f,now+100)
                event(MotionEvent.ACTION_UP,v.width*.75f,v.height*.6f,now+200)
                val vm=ViewModelProvider(a)[AppModel::class.java];assertEquals(1,vm.doc.segments.size)
            }
            compose.waitUntil(15000) {var saved=false;scenario.onActivity {saved=ViewModelProvider(it)[AppModel::class.java].saveStatus=="已保存"};saved}
            // A newly committed valid line must open the input without a second tap.
            compose.waitUntil(10000) {compose.onAllNodesWithText("输入实测尺寸").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasSetTextAction()).performTextInput("02500.00");screenshot("03-dimension-input");compose.onNodeWithText("确认").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("确认").fetchSemanticsNodes().isEmpty()}
            compose.waitForIdle()
            scenario.onActivity {a->val vm=ViewModelProvider(a)[AppModel::class.java];assertEquals("2500",vm.doc.segments.single().dimension);vm.undo();assertNull(vm.doc.segments.single().dimension);vm.redo();assertEquals("2500",vm.doc.segments.single().dimension)}
            compose.waitUntil(15000) {var saved=false;scenario.onActivity {saved=ViewModelProvider(it)[AppModel::class.java].saveStatus=="已保存"};saved}
            screenshot("04-editor")
            val saved=store.dao.allPhotos().first {it.id==photo.id};assertEquals("2500",store.document(saved).segments.single().dimension)
            compose.onNodeWithContentDescription("照片菜单").performClick()
            compose.onNodeWithText("标注设置").performClick()
            compose.onNodeWithContentDescription("画线后自动填写尺寸").performClick()
            compose.waitUntil(10000) {var disabled=false;scenario.onActivity {disabled= !ViewModelProvider(it)[AppModel::class.java].autoDimension};disabled}
            assertFalse(store.autoDimension())
            screenshot("05-input-setting");compose.onNodeWithText("完成").performClick();compose.waitForIdle()
            // Drawing with the setting off leaves the canvas available; tapping the line still edits it.
            scenario.onActivity {a->val v=canvas(a.window.decorView)!!;val bitmap=v.bitmap!!;val scale=kotlin.math.min(v.width.toFloat()/bitmap.width,v.height.toFloat()/bitmap.height)*.94f
                val left=(v.width-bitmap.width*scale)/2;val top=(v.height-bitmap.height*scale)/2
                val now=SystemClock.uptimeMillis();val y=top+bitmap.height*scale*.75f
                for((i,action) in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).withIndex()) {
                    val x=left+bitmap.width*scale*(.2f+i*.03f);val e=MotionEvent.obtain(now,now+i*100L,action,x,y,0);v.dispatchTouchEvent(e);e.recycle()
                }
            }
            compose.waitForIdle();compose.onNodeWithText("输入实测尺寸").assertDoesNotExist()
            scenario.onActivity {a->val v=canvas(a.window.decorView)!!;val bitmap=v.bitmap!!;val scale=kotlin.math.min(v.width.toFloat()/bitmap.width,v.height.toFloat()/bitmap.height)*.94f
                assertEquals(2,ViewModelProvider(a)[AppModel::class.java].doc.segments.size)
                val x=(v.width-bitmap.width*scale)/2+bitmap.width*scale*.23f;val y=(v.height-bitmap.height*scale)/2+bitmap.height*scale*.75f;val now=SystemClock.uptimeMillis()
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {val e=MotionEvent.obtain(now,now+10,action,x,y,0);v.dispatchTouchEvent(e);e.recycle()}
            }
            compose.waitUntil(10000) {compose.onAllNodesWithText("输入实测尺寸").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasSetTextAction()).performTextInput("50");compose.onNodeWithText("确认").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("确认").fetchSemanticsNodes().isEmpty()}
            screenshot("06-short-line")
        } finally {scenario.close();store.autoDimension(oldAuto);store.deleteProject(project);file.delete();store.db.close()}
    }
}
