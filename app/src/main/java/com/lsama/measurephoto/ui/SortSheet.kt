@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lsama.measurephoto.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lsama.measurephoto.data.Photo

@Composable fun SortSheet(photos: List<Photo>,dismiss: ()->Unit,save: (List<Photo>)->Unit) {
    var list by remember {mutableStateOf(photos)}
    val threshold=with(LocalDensity.current) {64.dp.toPx()}
    fun move(id: String,delta: Int) {val index=list.indexOfFirst {it.id==id};val target=index+delta;if(target in list.indices) {val next=list.toMutableList();val p=next.removeAt(index);next.add(target,p);list=next}}
    ModalBottomSheet(onDismissRequest=dismiss) {Column(Modifier.padding(horizontal=20.dp).padding(bottom=24.dp)) {
        Text("照片排序",style=MaterialTheme.typography.titleLarge);Text("长按把手拖动，或使用上下按钮。",color=Quiet)
        LazyColumn(Modifier.fillMaxWidth().weight(1f,false)) {itemsIndexed(list,key={_,p->p.id}) {index,p->
            ListItem(headlineContent={Text("${index+1}  ${p.roomName.ifBlank {"现场照片"}}")},leadingContent={Thumbnail(p,Modifier.size(48.dp))},trailingContent={Row {
                Tool(Icons.Outlined.KeyboardArrowUp,"上移",index>0){move(p.id,-1)};Tool(Icons.Outlined.KeyboardArrowDown,"下移",index<list.lastIndex){move(p.id,1)}
                Icon(Icons.Outlined.DragHandle,"长按拖动排序",Modifier.size(48.dp).pointerInput(p.id) {var total=0f;detectDragGesturesAfterLongPress(onDragStart={total=0f},onDrag={change,amount->change.consume();total+=amount.y;if(total>threshold){move(p.id,1);total=0f}else if(total< -threshold){move(p.id,-1);total=0f}})})
            }})
        }}
        Primary("保存顺序",modifier=Modifier.fillMaxWidth()){save(list)}
    }}
}
