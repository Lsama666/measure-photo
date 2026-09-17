package com.lsama.measurephoto.model

import org.junit.Assert.*
import org.junit.Test

class LabelPlacementTest {
    @Test fun shortLinesRemainVisibleAtEveryAngleAndImageEdge() {
        for(center in listOf(Point(180f,180f),Point(4f,4f),Point(355f,4f),Point(4f,355f),Point(355f,355f))) {
            for(delta in listOf(Point(10f,0f),Point(0f,10f),Point(7f,7f),Point(-7f,7f))) {
                val end=Point((center.x+delta.x).coerceIn(0f,360f),(center.y+delta.y).coerceIn(0f,360f))
                val box=placeLabel(center,end,360f,360f,90f,30f,6f)
                assertFalse("$center -> $end, $box",box.intersectsLine(center,end,2f))
                assertTrue(box.left>=0 && box.top>=0 && box.right<=360 && box.bottom<=360)
            }
        }
    }
    @Test fun strokeDirectionDoesNotFlipTheLabel() {
        val a=Point(20f,60f);val b=Point(100f,220f)
        assertEquals(placeLabel(a,b,360f,360f,80f,30f,6f),placeLabel(b,a,360f,360f,80f,30f,6f))
    }
    @Test fun photoResolutionDoesNotChangeNormalizedPlacement() {
        val small=placeLabel(Point(20f,60f),Point(30f,70f),360f,480f,80f,30f,6f)
        val big=placeLabel(Point(200f,600f),Point(300f,700f),3600f,4800f,800f,300f,60f)
        assertEquals(small.left,big.left/10,.0001f);assertEquals(small.top,big.top/10,.0001f)
    }
    @Test fun manuallyPositionedExistingNumbersKeepTheirOffset() {
        val box=placeLabel(Point(100f,100f),Point(200f,100f),360f,360f,80f,30f,6f,Point(30f,50f))
        assertEquals(Point(180f,150f),box.center)
    }
    @Test fun oversizeLabelStillFitsImage() {
        val box=placeLabel(Point(0f,0f),Point(1f,1f),20f,30f,100f,80f,6f)
        assertEquals(LabelBox(0f,0f,20f,30f),box)
    }
}
