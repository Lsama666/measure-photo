package com.lsama.measurephoto.model

import kotlin.math.*

/** Image-space bounds, shared by preview, hit testing, thumbnails and exports. */
data class LabelBox(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    val center get()=Point((left+right)/2,(top+bottom)/2)
    fun intersectsLine(a: Point,b: Point,padding: Float=0f): Boolean {
        val halfW=(right-left)/2+padding;val halfH=(bottom-top)/2+padding
        val dx=(b.x-a.x)/2;val dy=(b.y-a.y)/2
        val cx=(a.x+b.x)/2-center.x;val cy=(a.y+b.y)/2-center.y
        if(abs(cx)>halfW+abs(dx) || abs(cy)>halfH+abs(dy)) return false
        return abs(dx*cy-dy*cx)<=halfW*abs(dy)+halfH*abs(dx)
    }
}

fun placeLabel(a: Point,b: Point,imageWidth: Float,imageHeight: Float,labelWidth: Float,labelHeight: Float,
    gap: Float,manualOffset: Point?=null): LabelBox {
    val w=labelWidth.coerceIn(0f,imageWidth);val h=labelHeight.coerceIn(0f,imageHeight)
    val mid=Point((a.x+b.x)/2,(a.y+b.y)/2)
    fun box(center: Point): LabelBox {
        val x=(center.x-w/2).coerceIn(0f,imageWidth-w);val y=(center.y-h/2).coerceIn(0f,imageHeight-h)
        return LabelBox(x,y,x+w,y+h)
    }
    // Existing dragged positions remain anchored to the midpoint, without a schema migration.
    if(manualOffset!=null) return box(Point(mid.x+manualOffset.x,mid.y+manualOffset.y))
    val length=max(.0001f,a.distance(b));val dx=b.x-a.x;val dy=b.y-a.y
    var nx=dy/length;var ny= -dx/length
    // Prefer above horizontal lines and the right side of vertical lines, independent of stroke direction.
    if(ny>0 || (abs(ny)<.0001f && nx<0)) {nx= -nx;ny= -ny}
    val distance=abs(nx)*w/2+abs(ny)*h/2+gap
    val candidates=listOf(
        Point(mid.x+nx*distance,mid.y+ny*distance),Point(mid.x-nx*distance,mid.y-ny*distance),
        Point(mid.x, min(a.y,b.y)-h/2-gap),Point(mid.x,max(a.y,b.y)+h/2+gap),
        Point(min(a.x,b.x)-w/2-gap,mid.y),Point(max(a.x,b.x)+w/2+gap,mid.y)
    ).map(::box)
    return candidates.firstOrNull {!it.intersectsLine(a,b,gap/2)} ?: candidates.first()
}
