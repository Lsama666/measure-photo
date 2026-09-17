package com.lsama.measurephoto.model

import java.math.BigDecimal
import java.util.UUID
import kotlin.math.*

fun uid(): String = UUID.randomUUID().toString()
data class Point(val x: Float, val y: Float) {
    fun distance(p: Point) = hypot(x-p.x, y-p.y)
    fun bounded() = Point(x.coerceIn(0f,1f), y.coerceIn(0f,1f))
}
data class Vertex(val id: String = uid(), val x: Float, val y: Float) { val point get() = Point(x,y) }
data class Pen(val color: Int = 0xFFC23D31.toInt(), val width: Float = 3f, val font: Float = 20f)
data class Segment(val id: String = uid(), val start: String, val end: String, val dimension: String? = null,
    val pen: Pen = Pen(), val offsetX: Float = 0f, val offsetY: Float = 0f)
data class Document(val schemaVersion: Int = 1, val revision: Long = 0, val vertices: List<Vertex> = emptyList(), val segments: List<Segment> = emptyList()) {
    fun vertex(id: String) = vertices.first { it.id == id }
    fun changed() = copy(revision=revision+1)
    fun clean(): Document { val used=segments.flatMap { listOf(it.start,it.end) }.toSet(); return copy(vertices=vertices.filter { it.id in used }) }
    fun add(a: Point, b: Point, aId: String?, bId: String?, pen: Pen): Document {
        val av = vertices.find { it.id==aId } ?: Vertex(x=a.bounded().x,y=a.bounded().y)
        val bv = vertices.find { it.id==bId } ?: Vertex(x=b.bounded().x,y=b.bounded().y)
        if(av.id==bv.id || av.point.distance(bv.point)<0.00001f || segments.any { setOf(it.start,it.end)==setOf(av.id,bv.id) }) return this
        return copy(vertices=(vertices+av+bv).distinctBy { it.id },segments=segments+Segment(start=av.id,end=bv.id,pen=pen)).changed()
    }
    fun move(id: String, p: Point) = copy(vertices=vertices.map { if(it.id==id) it.copy(x=p.bounded().x,y=p.bounded().y) else it })
    fun merge(source: String,target: String): Document {
        if(source==target) return this
        val edges=segments.map { it.copy(start=if(it.start==source) target else it.start,end=if(it.end==source) target else it.end) }
        // Reject destructive merges: no dimensions or edges are silently discarded.
        if(edges.any { it.start==it.end } || edges.map { setOf(it.start,it.end) }.distinct().size!=edges.size) return this
        return copy(segments=edges).clean()
    }
    fun update(id: String, f: (Segment)->Segment) = copy(segments=segments.map { if(it.id==id) f(it) else it }).changed()
    fun delete(id: String) = copy(segments=segments.filterNot { it.id==id }).clean().changed()
}
fun dimension(raw: String): String? {
    val s=raw.trim()
    if(s.isEmpty()) return null
    require(Regex("[0-9]{1,8}(\\.[0-9]{1,2})?").matches(s)) { "请输入最多 8 位整数、2 位小数的正数" }
    val number=BigDecimal(s)
    require(number>BigDecimal.ZERO) { "尺寸必须大于 0；留空表示未填写" }
    return number.stripTrailingZeros().toPlainString()
}
data class Transform(val width: Float,val height: Float,val scale: Float,val tx: Float,val ty: Float) {
    fun screen(p: Point) = Point(p.x*width*scale+tx,p.y*height*scale+ty)
    fun inverse(p: Point) = Point((p.x-tx)/(width*scale),(p.y-ty)/(height*scale))
    fun snap(doc: Document,p: Point,px: Float,excluded: String?=null): Vertex? = doc.vertices.filter { it.id!=excluded }
        .minByOrNull { screen(it.point).distance(p) }?.takeIf { screen(it.point).distance(p)<=px }
}
fun lineDistance(p: Point,a: Point,b: Point): Float {
    val dx=b.x-a.x; val dy=b.y-a.y; val len=dx*dx+dy*dy
    if(len==0f) return p.distance(a)
    val t=(((p.x-a.x)*dx+(p.y-a.y)*dy)/len).coerceIn(0f,1f)
    return p.distance(Point(a.x+t*dx,a.y+t*dy))
}
class History(initial: Document) {
    var current=initial; private set
    private val past=ArrayDeque<Document>(); private val future=ArrayDeque<Document>()
    val canUndo get()=past.isNotEmpty(); val canRedo get()=future.isNotEmpty()
    fun commit(next: Document): Boolean { if(next==current) return false; past.addLast(current); while(past.size>75) past.removeFirst(); future.clear(); current=next.copy(revision=current.revision+1); return true }
    fun undo(): Document { if(canUndo) {future.addLast(current);current=past.removeLast().copy(revision=current.revision+1)};return current }
    fun redo(): Document { if(canRedo) {past.addLast(current);current=future.removeLast().copy(revision=current.revision+1)};return current }
}
/** A second pointer cancels the draft, and blocks drawing until every pointer is up. */
class GestureGate {
    var blocked=false; private set
    fun secondPointer() { blocked=true }
    fun allUp() { blocked=false }
}
