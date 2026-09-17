package com.lsama.measurephoto.model
import org.junit.Test
import org.junit.Assert.*
import com.google.gson.Gson

class GeometryTest {
    private fun edge()=Document().add(Point(.1f,.1f),Point(.8f,.2f),null,null,Pen())
    @Test fun coordinatesRoundTrip() {for(w in listOf(400f,4000f)) for(h in listOf(300f,6000f)) for(z in listOf(.1f,1f,8f)) {val t=Transform(w,h,z,31f,-44f);val p=Point(.23f,.89f);assertTrue(t.inverse(t.screen(p)).distance(p)<.000001f)}}
    @Test fun screenSnapDistance() {val d=edge();for(z in listOf(.1f,1f,8f)) {val t=Transform(4000f,3000f,z,0f,0f);val p=t.screen(d.vertices.first().point);assertNotNull(t.snap(d,Point(p.x+15,p.y),16f));assertNull(t.snap(d,Point(p.x+17,p.y),16f))}}
    @Test fun closedVerticesRemainShared() {var d=edge();val first=d.vertices[0].id;val second=d.vertices[1].id;d=d.add(Point(.8f,.2f),Point(.8f,.8f),second,null,Pen());val third=d.vertices.last().id;d=d.add(Point(.8f,.8f),Point(.1f,.8f),third,null,Pen());val fourth=d.vertices.last().id;d=d.add(Point(.1f,.8f),Point(.1f,.1f),fourth,first,Pen());assertEquals(4,d.vertices.size);val moved=d.move(first,Point(.2f,.2f));assertEquals(2,moved.segments.count {it.start==first || it.end==first});assertEquals(.2f,moved.vertex(first).x)}
    @Test fun invalidAndCrossingEdges() {val d=edge();val a=d.vertices[0];val b=d.vertices[1];assertEquals(d,d.add(a.point,a.point,a.id,a.id,Pen()));assertEquals(d,d.add(b.point,a.point,b.id,a.id,Pen()));val cross=d.add(Point(.5f,0f),Point(.5f,1f),null,null,Pen());assertEquals(4,cross.vertices.size)}
    @Test fun historyRestoresGraphAndDimension() {val d=edge();val h=History(d);val id=d.segments[0].id;h.commit(d.update(id){it.copy(dimension="2500")});h.commit(h.current.delete(id));assertTrue(h.current.vertices.isEmpty());assertEquals("2500",h.undo().segments.first().dimension);assertEquals(d.vertices,h.current.vertices);assertNull(h.undo().segments.first().dimension);assertEquals("2500",h.redo().segments.first().dimension)}
    @Test fun dimensionValidation() {assertEquals("2500",dimension("02500.00"));assertEquals("1250.5",dimension("1250.50"));assertNull(dimension(""));for(s in listOf("0","-1","1e3","1.234","100000000","NaN","1.",".1")) assertThrows(IllegalArgumentException::class.java){dimension(s)}}
    @Test fun independentGeometryAndValues() {val d=edge();val s=d.segments.first();val n=d.update(s.id){it.copy(dimension="2500")};assertEquals(d.vertices,n.vertices);assertEquals("2500",n.move(s.start,Point(.3f,.4f)).segments[0].dimension)}
    @Test fun mergeCanBeUndone() {var d=edge();d=d.add(Point(.6f,.6f),Point(.9f,.9f),null,null,Pen());val h=History(d);val merged=d.merge(d.vertices[2].id,d.vertices[0].id);assertEquals(3,merged.vertices.size);h.commit(merged);assertEquals(d.vertices,h.undo().vertices);assertEquals(merged.vertices,h.redo().vertices)}
    @Test fun secondFingerBlocksUntilAllUp() {val gate=GestureGate();gate.secondPointer();assertTrue(gate.blocked);assertTrue(gate.blocked);gate.allUp();assertFalse(gate.blocked)}
    @Test fun jsonRoundTrip() {val d=edge().let {it.update(it.segments.first().id){s->s.copy(dimension="12.5",offsetX=.2f,pen=Pen(123,5f,24f))}};val g=Gson();assertEquals(d,g.fromJson(g.toJson(d),Document::class.java))}
    @Test fun historyRetainsFiftySteps() {val d=edge();val h=History(d);repeat(60){i->h.commit(h.current.update(d.segments[0].id){it.copy(dimension="${i+1}")})};repeat(50){h.undo()};assertEquals("10",h.current.segments[0].dimension);h.commit(h.current.update(d.segments[0].id){it.copy(dimension="99")});assertFalse(h.canRedo)}
}
