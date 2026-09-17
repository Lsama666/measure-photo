@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lsama.measurephoto.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lsama.measurephoto.AppModel
import com.lsama.measurephoto.editor.PhotoCanvas
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.render.Photos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun EditorScreen(vm: AppModel,onBack: ()->Unit,onCamera: ()->Unit) {
    val photo=vm.editing?:return
    var drawMode by rememberSaveable(photo.id) {mutableStateOf(true)}
    var dimensionId by remember {mutableStateOf<String?>(null)}
    var room by remember {mutableStateOf(false)}
    var note by remember {mutableStateOf(false)}
    var noteExpanded by remember {mutableStateOf(true)}
    var fit by remember {mutableIntStateOf(0)}
    var more by remember {mutableStateOf(false)}
    var settings by remember {mutableStateOf(false)}
    val bitmap by produceState<Bitmap?>(null,photo.id) {try {value=withContext(Dispatchers.IO) {Photos.decode(photo.originalPath,2048)}} catch(t: Exception){vm.error="照片加载失败：${t.message}"}}
    val lifecycle=LocalLifecycleOwner.current
    DisposableEffect(lifecycle,photo.id) {val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_STOP) vm.flush()};lifecycle.lifecycle.addObserver(observer);onDispose {lifecycle.lifecycle.removeObserver(observer)}}
    BackHandler {when {dimensionId!=null->dimensionId=null;note->{note=false;vm.flush()};vm.selected!=null->vm.selected=null;else->onBack()}}
    Scaffold(containerColor=Paper,modifier=Modifier.imePadding(),topBar={Column {
        TopAppBar(title={Column {Text("照片标注",fontSize=18.sp);Text(vm.saveStatus,fontSize=12.sp,color=if(vm.saveStatus=="保存失败") MaterialTheme.colorScheme.error else Quiet)}},navigationIcon={Tool(Icons.Outlined.ArrowBack,"返回",onClick=onBack)},actions={
            Primary("保存",modifier=Modifier.padding(end=4.dp),onClick=onBack)
            Box {Tool(Icons.Outlined.MoreVert,"照片菜单"){more=true};DropdownMenu(more,{more=false}) {DropdownMenuItem({Text("标注设置")},{settings=true;more=false});DropdownMenuItem({Text("适应屏幕")},{fit++;more=false});DropdownMenuItem({Text("继续拍照")},{more=false;onCamera()});DropdownMenuItem({Text("完成并返回")},{more=false;onBack()})}}
        },colors=TopAppBarDefaults.topAppBarColors(containerColor=Paper))
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).background(Color.White,androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable {room=true}.heightIn(min=52.dp).padding(horizontal=14.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.MeetingRoom,null,Modifier.size(21.dp),Green);Spacer(Modifier.width(10.dp));Text("房间（选填）",fontSize=14.sp,color=Quiet);Spacer(Modifier.width(12.dp));Text(photo.roomName.ifBlank {"点击填写"},fontSize=15.sp,color=Green,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp),Quiet)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=4.dp)) {Text(if(drawMode) "拖动画直线 · 双指缩放" else "拖动端点或数字 · 空白处平移",fontSize=12.sp,color=Quiet,modifier=Modifier.weight(1f));Text("单位 mm",fontSize=12.sp,color=Quiet)}
    }},bottomBar={Surface(color=Color.White) {Column(Modifier.navigationBarsPadding()) {
        val active=vm.doc.segments.find {it.id==vm.selected};val pen=active?.pen?:vm.pen
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            val colors=listOf(0xFFC23D31,0xFFE7B52E,0xFF2768B2,0xFF287650,0xFFFFFFFF,0xFF232725)
            var colorMenu by remember {mutableStateOf(false)}
            Box {TextButton({colorMenu=true}) {Box(Modifier.size(16.dp).border(1.dp,Rule,CircleShape).background(Color(pen.color),CircleShape));Spacer(Modifier.width(6.dp));Text("颜色")}
                DropdownMenu(colorMenu,{colorMenu=false}) {colors.forEachIndexed {index,c->DropdownMenuItem(text={Text(listOf("红色","黄色","蓝色","绿色","白色","深灰色")[index])},leadingIcon={Box(Modifier.size(20.dp).border(1.dp,Rule,CircleShape).background(Color(c),CircleShape))},onClick={vm.style(pen.copy(color=c.toInt()));colorMenu=false})}}
            }
            var widthMenu by remember {mutableStateOf(false)};Box {TextButton({widthMenu=true}){Text(when(pen.width){1.5f->"细线";5f->"粗线";else->"中线"})};DropdownMenu(widthMenu,{widthMenu=false}) {listOf(1.5f,3f,5f).forEachIndexed {i,w->DropdownMenuItem({Text(listOf("细线","中线","粗线")[i])},{vm.style(pen.copy(width=w));widthMenu=false})}}}
            var fontMenu by remember {mutableStateOf(false)};Box {TextButton({fontMenu=true}){Text("字号")};DropdownMenu(fontMenu,{fontMenu=false}) {listOf(16f,20f,24f).forEachIndexed {i,f->DropdownMenuItem({Text(listOf("小字","中字","大字")[i])},{vm.style(pen.copy(font=f));fontMenu=false})}}}
            if(active!=null) Tool(Icons.Outlined.DeleteOutline,"删除选中线"){vm.commit(vm.doc.delete(active.id));vm.selected=null}
        }
        HorizontalDivider(color=Rule)
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
            EditorTool(Icons.Outlined.Edit,"画线",drawMode){drawMode=true;vm.selected=null}
            EditorTool(Icons.Outlined.NearMe,"选择",!drawMode){drawMode=false}
            EditorTool(Icons.Outlined.Notes,"备注",false){note=true}
            EditorTool(Icons.Outlined.Undo,"撤销",false,vm.canUndo){vm.undo()}
            EditorTool(Icons.Outlined.Redo,"重做",false,vm.canRedo){vm.redo()}
        }
    }}}) {padding->Column(Modifier.padding(padding).fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory={PhotoCanvas(it)},modifier=Modifier.fillMaxSize().semantics {contentDescription="照片标注画布"},update={view->
                view.bitmap=bitmap;view.document=vm.doc;view.drawMode=drawMode;view.pen=vm.pen;view.selected=vm.selected;view.fitToken=fit
                view.onCommit={vm.commit(it)};view.onSelect={vm.selected=it};view.onDimension={dimensionId=it};view.invalidate()
                view.onLineCreated={id->if(vm.autoDimension) {vm.selected=id;dimensionId=id}}
            })
            if(bitmap==null) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Green)
            if(vm.selected!=null && vm.doc.segments.find {it.id==vm.selected}?.dimension==null) TextButton({dimensionId=vm.selected},Modifier.align(Alignment.BottomCenter)){Text("填写尺寸")}
        }
        if(photo.note.isNotBlank()) Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal=20.dp,vertical=8.dp)) {
            Row(Modifier.fillMaxWidth().clickable {noteExpanded=!noteExpanded},verticalAlignment=Alignment.CenterVertically) {Text("现场备注",fontSize=12.sp,color=Quiet,modifier=Modifier.weight(1f));Icon(if(noteExpanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,"展开或收起备注",Modifier.size(20.dp))}
            if(noteExpanded) Text(photo.note,Modifier.fillMaxWidth().heightIn(max=90.dp).verticalScroll(rememberScrollState()).clickable {note=true}.padding(top=6.dp),fontSize=13.sp)
        }
    }}
    if(settings) AlertDialog(onDismissRequest={settings=false},title={Text("标注设置")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("画线后自动填写尺寸",Modifier.weight(1f));Switch(vm.autoDimension,{vm.updateAutoDimension(it)},Modifier.semantics {contentDescription="画线后自动填写尺寸"})
        }
        Text("关闭后，点击线或数字填写尺寸。两种方式都可以点击修改，取消输入会保留线条。",fontSize=14.sp,color=Quiet)
    }},confirmButton={TextButton({settings=false}){Text("完成")}})
    if(room) ModalBottomSheet(onDismissRequest={room=false}) {var value by remember {mutableStateOf(photo.roomName)};Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("房间",style=MaterialTheme.typography.titleLarge);OutlinedTextField(value,{if(it.length<=30)value=it},label={Text("可留空，也可自由填写")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(Modifier.horizontalScroll(rememberScrollState())) {listOf("厨房","客厅","主卧","次卧","阳台","卫生间").forEach {TextButton({value=it}){Text(it)}}}
        Row {TextButton({value=""}){Text("清空")};Spacer(Modifier.weight(1f));Primary("保存"){vm.room(value);room=false}}
    }}
    if(note) ModalBottomSheet(onDismissRequest={note=false;vm.flush()},modifier=Modifier.imePadding()) {Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("现场备注",style=MaterialTheme.typography.titleLarge);OutlinedTextField(photo.note,{vm.note(it)},modifier=Modifier.fillMaxWidth().heightIn(min=140.dp,max=250.dp),placeholder={Text("例如：右侧有燃气管，预留检修空间")},supportingText={Text("${photo.note.length} / 1000")});Primary("完成",modifier=Modifier.fillMaxWidth()){vm.flush();note=false}
    }}
    dimensionId?.let {id->val s=vm.doc.segments.find {it.id==id};if(s!=null) {
        var value by remember(id) {mutableStateOf(s.dimension?:"")};var issue by remember {mutableStateOf<String?>(null)}
        val focus=remember {FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
        fun submit() {try {val normalized=dimension(value);vm.commit(vm.doc.update(id){it.copy(dimension=normalized)});keyboard?.hide();dimensionId=null} catch(t: IllegalArgumentException) {issue=t.message}}
        // Inline input keeps the photo visible and uses adjustResize for the system keyboard.
        ModalBottomSheet(onDismissRequest={keyboard?.hide();dimensionId=null},modifier=Modifier.imePadding(),sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.padding(horizontal=24.dp).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("尺寸",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(value,{value=it;issue=null},modifier=Modifier.fillMaxWidth().focusRequester(focus),singleLine=true,suffix={Text("mm")},placeholder={Text("输入实测尺寸")},isError=issue!=null,supportingText={issue?.let {Text(it)}},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {TextButton({keyboard?.hide();dimensionId=null}){Text("取消")};Spacer(Modifier.weight(1f));Primary("确认"){submit()}}
            }
            LaunchedEffect(id) {focus.requestFocus();keyboard?.show()}
        }
    }}
}
@Composable private fun EditorTool(icon: androidx.compose.ui.graphics.vector.ImageVector,label: String,selected: Boolean,enabled: Boolean=true,onClick: ()->Unit) {
    Column(Modifier.widthIn(min=56.dp).clickable(enabled=enabled,onClick=onClick).padding(vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Icon(icon,null,Modifier.size(22.dp),tint=if(!enabled) Quiet.copy(alpha=.35f) else if(selected) Green else Quiet);Spacer(Modifier.height(4.dp));Text(label,fontSize=11.sp,color=if(!enabled) Quiet.copy(alpha=.35f) else if(selected) Green else Quiet)
    }
}
