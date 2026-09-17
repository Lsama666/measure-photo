@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.lsama.measurephoto.ui

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lsama.measurephoto.AppModel
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.export.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun Thumbnail(p: Photo?,modifier: Modifier=Modifier) {
    val bitmap by produceState<Bitmap?>(null,p?.thumbnailPath,p?.updatedAt) {value=withContext(Dispatchers.IO) {p?.let {BitmapFactory.decodeFile(it.thumbnailPath)}}}
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(Rule),contentAlignment=Alignment.Center) {
        bitmap?.let {Image(it.asImageBitmap(),"现场照片",Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:Icon(Icons.Outlined.CropFree,null,tint=Quiet)
    }
}
@Composable fun TextDialog(title: String,initial: String,max: Int=60,allowEmpty: Boolean=false,onDismiss: ()->Unit,onConfirm: (String)->Unit) {
    var value by remember {mutableStateOf(initial)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Column {OutlinedTextField(value,{if(it.length<=max)value=it},singleLine=true,label={Text(title)},modifier=Modifier.fillMaxWidth());Text("${value.length} / $max",color=Quiet,fontSize=12.sp)}},
        confirmButton={TextButton({onConfirm(value.trim());onDismiss()},enabled=allowEmpty || value.isNotBlank()) {Text("保存")}},dismissButton={TextButton(onDismiss) {Text("取消")}})
}
@Composable fun Confirm(title: String,body: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton({onConfirm();onDismiss()}) {Text("确认",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onDismiss) {Text("取消")}})
}
@Composable fun MeasureApp(vm: AppModel) = MeasureTheme {
    val projects by vm.projects.collectAsState();val photos by vm.photos.collectAsState();val jobs by vm.jobs.collectAsState()
    var projectId by rememberSaveable {mutableStateOf<String?>(null)}
    var photoId by rememberSaveable {mutableStateOf<String?>(null)}
    var camera by rememberSaveable {mutableStateOf(false)}
    var selected by remember {mutableStateOf(setOf<String>())}
    var selection by rememberSaveable {mutableStateOf(false)}
    var newProject by remember {mutableStateOf(false)}
    var rename by remember {mutableStateOf<Project?>(null)}
    var deleteProject by remember {mutableStateOf<Project?>(null)}
    var deletePhotos by remember {mutableStateOf(false)}
    var sort by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    var exportPanel by rememberSaveable {mutableStateOf(false)}
    var album by rememberSaveable {mutableStateOf(true)}
    var pdf by rememberSaveable {mutableStateOf(true)}
    var warning by remember {mutableStateOf<ExportJob?>(null)}
    var stopConfirm by remember {mutableStateOf(false)}
    var history by remember {mutableStateOf(false)}
    val ctx=LocalContext.current
    val project=projects.find {it.id==projectId};val projectPhotos=photos.filter {it.projectId==projectId}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) {uris->projectId?.let {vm.import(it,uris){p->if(p!=null){vm.open(p);photoId=p.id}}}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted->if(granted) camera=true else vm.error="未获得相机权限。你仍可以从相册导入照片；也可到系统设置开启相机权限。"}
    val pdfLocation=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {uri->val j=vm.pendingExport;vm.pendingExport=null;if(uri!=null && j!=null) {
        runCatching {ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)}
        j.pdfUri=uri.toString();vm.export(j);exportPanel=false
    } else if(j!=null) exportPanel=true}
    fun startExport(j: ExportJob) {if(j.pdf && !j.pdfDone) {vm.pendingExport=j;pdfLocation.launch("${safeName(j.project)}_量尺记录_${j.timestamp}.pdf")} else {vm.export(j);exportPanel=false}}
    LaunchedEffect(photoId,photos) {if(photoId!=null && vm.editing?.id!=photoId) photos.find {it.id==photoId}?.let {vm.open(it)}}
    BackHandler(enabled=projectId!=null || vm.activeExport!=null) {
        when {vm.activeExport!=null->{if(vm.exporting) stopConfirm=true else vm.activeExport=null};camera->camera=false;photoId!=null->vm.leave {photoId=null};selection->{selection=false;selected=emptySet()};else->projectId=null}
    }
    Surface(Modifier.fillMaxSize(),color=Paper) {
        if(camera && projectId!=null) CameraScreen(onBack={camera=false},onCaptured={file->camera=false;vm.import(projectId!!,listOf(Uri.fromFile(file))){p->file.delete();if(p!=null){vm.open(p);photoId=p.id}}},onError={vm.error=it})
        else if(photoId!=null && vm.editing!=null) EditorScreen(vm,onBack={vm.leave {photoId=null}},onCamera={vm.leave {photoId=null;permission.launch(Manifest.permission.CAMERA)}})
        else Scaffold(containerColor=Paper,topBar={Column {
            TopAppBar(title={Text(if(project==null) "项目" else project.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                navigationIcon={if(project!=null) Tool(Icons.Outlined.ArrowBack,"返回项目"){projectId=null;selection=false;selected=emptySet()}},
                actions={if(project==null) {Tool(Icons.Outlined.Info,"关于"){about=true};TextButton({newProject=true}){Text("新建")}}
                else {TextButton({selection=!selection;selected=emptySet()}){Text(if(selection) "取消" else "选择")};var more by remember {mutableStateOf(false)};Box {Tool(Icons.Outlined.MoreVert,"项目菜单"){more=true};DropdownMenu(more,{more=false}) {
                    DropdownMenuItem({Text("照片排序")},{sort=true;more=false});DropdownMenuItem({Text("重命名项目")},{rename=project;more=false});DropdownMenuItem({Text("导出记录")},{history=true;more=false})
                }}}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Paper))
            if(project!=null) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(selection) "已选 ${selected.size} 张" else "${projectPhotos.size} 张现场照片",color=Quiet,modifier=Modifier.weight(1f));if(selection) TextButton({selected=if(selected.size==projectPhotos.size) emptySet() else projectPhotos.map {it.id}.toSet()}){Text("全选")}
                else Text("本机保存",color=Quiet,fontSize=12.sp)
            }
        }},bottomBar={if(project!=null) Surface(color=androidx.compose.ui.graphics.Color.White) {Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=20.dp,vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            if(selection) {TextButton({deletePhotos=true},enabled=selected.isNotEmpty()){Text("删除",color=MaterialTheme.colorScheme.error)};Primary("导出 ${selected.size} 张",Icons.Outlined.IosShare,Modifier.weight(1f),selected.isNotEmpty()){exportPanel=true}}
            else {TextButton({picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Icon(Icons.Outlined.AddPhotoAlternate,null);Spacer(Modifier.width(6.dp));Text("导入")};Primary("拍照",Icons.Outlined.PhotoCamera,Modifier.weight(1f)){permission.launch(Manifest.permission.CAMERA)};Tool(Icons.Outlined.IosShare,"导出全部",projectPhotos.isNotEmpty()){exportPanel=true}}
        }}}) {padding->
            if(project==null) {
                if(projects.isEmpty()) Column(Modifier.padding(padding).fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.Start) {
                    Icon(Icons.Outlined.Straighten,null,Modifier.size(48.dp),tint=Green);Spacer(Modifier.height(24.dp));Text("把尺寸，记在现场。",fontSize=25.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(12.dp));Text("建立一个项目，拍下现场照片。\n画线、记尺寸，整理成完整的量尺记录。",color=Quiet,lineHeight=25.sp);Spacer(Modifier.height(32.dp));Primary("新建项目",Icons.Outlined.Add){newProject=true}
                } else LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp)) {
                    items(projects,key={it.id}) {p->val items=photos.filter {it.projectId==p.id};var more by remember {mutableStateOf(false)}
                        Row(Modifier.fillMaxWidth().clickable {projectId=p.id}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                            Thumbnail(items.firstOrNull(),Modifier.size(68.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){Text(p.name,fontSize=17.sp,fontWeight=FontWeight.Medium,maxLines=2);Spacer(Modifier.height(6.dp));Text("${items.size} 张照片 · "+SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(Date(p.updatedAt)),color=Quiet,fontSize=12.sp)}
                            Box {Tool(Icons.Outlined.MoreVert,"${p.name} 更多"){more=true};DropdownMenu(more,{more=false}) {DropdownMenuItem({Text("重命名")},{rename=p;more=false});DropdownMenuItem({Text("删除项目")},{deleteProject=p;more=false})}}
                        };HorizontalDivider(color=Rule)
                    }
                }
            } else if(projectPhotos.isEmpty()) Column(Modifier.padding(padding).fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center) {Icon(Icons.Outlined.PhotoCamera,null,Modifier.size(40.dp),Green);Spacer(Modifier.height(20.dp));Text("从第一张现场照片开始",fontSize=21.sp);Spacer(Modifier.height(10.dp));Text("房间可以稍后填写。\n先拍下需要记录尺寸的位置。",color=Quiet,lineHeight=24.sp)}
            else LazyVerticalGrid(GridCells.Fixed(2),Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                itemsIndexed(projectPhotos,key={_,p->p.id}) {index,p->Column(Modifier.combinedClickable(onClick={if(selection) selected=if(p.id in selected) selected-p.id else selected+p.id else {vm.open(p);photoId=p.id}},onLongClick={selection=true;selected=selected+p.id})) {
                    Box {Thumbnail(p,Modifier.fillMaxWidth().aspectRatio(1f));if(selection) Checkbox(p.id in selected,{selected=if(it)selected+p.id else selected-p.id},Modifier.align(Alignment.TopEnd).background(Paper.copy(alpha=.9f),RoundedCornerShape(8.dp)))}
                    Spacer(Modifier.height(9.dp));Text("${(index+1).toString().padStart(2,'0')}   ${p.roomName.ifBlank {"现场照片"}}",fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${vm.store.document(p).segments.size} 条标注",color=Quiet,fontSize=12.sp)
                }}
            }
        }
    }
    if(newProject) TextDialog("新建项目","",onDismiss={newProject=false}) {vm.create(it){id->projectId=id}}
    rename?.let {p->TextDialog("项目名称",p.name,onDismiss={rename=null}) {name->vm.action {vm.store.dao.project(p.copy(name=name,updatedAt=System.currentTimeMillis()))}}}
    deleteProject?.let {p->Confirm("删除项目？","项目内部照片与标注将被删除。已导出的相册图片和 PDF 不受影响。",{deleteProject=null}) {vm.action {vm.store.deleteProject(p)}}}
    if(deletePhotos) Confirm("删除 ${selected.size} 张照片？","删除照片及其可编辑标注。已导出的文件会保留。",{deletePhotos=false}) {vm.action {vm.store.deletePhotos(projectPhotos.filter {it.id in selected});selected=emptySet();selection=false}}
    if(about) AlertDialog(onDismissRequest={about=false},title={Text("现场量尺")},text={Text("人工量尺，照片记录。\n\n数据保存在本机。卸载或清空 APP 数据会丢失可编辑项目；相册图片和 PDF 是导出成品，不是可重新编辑的项目备份。\n\n测试版 0.2.1")},confirmButton={TextButton({about=false}){Text("知道了")}})
    if(sort) SortSheet(projectPhotos,{sort=false}) {list->vm.action {vm.store.dao.order(list);sort=false}}
    if(exportPanel && project!=null) ModalBottomSheet(onDismissRequest={exportPanel=false},containerColor=Paper) {
        val list=if(selection) projectPhotos.filter {it.id in selected} else projectPhotos
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("导出量尺记录",style=MaterialTheme.typography.titleLarge);Text("${list.size} 张照片 · 按当前顺序",color=Quiet)
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(album,{album=it});Text("保存图片到相册")};Row(verticalAlignment=Alignment.CenterVertically){Checkbox(pdf,{pdf=it});Text("生成汇总 PDF")}
            Text("原图与可编辑标注仍保存在项目内。",color=Quiet,fontSize=12.sp)
            Primary("导出",Icons.Outlined.IosShare,Modifier.fillMaxWidth(),list.isNotEmpty() && (album||pdf)) {
                val j=ExportJob(project=project.name,items=list.map {p->ExportItem(p,projectPhotos.indexOf(p)+1,vm.store.document(p))},album=album,pdf=pdf)
                if(j.items.sumOf {it.doc.segments.count {s->s.dimension==null}}>0) warning=j else startExport(j)
            }
        }
    }
    warning?.let {j->AlertDialog(onDismissRequest={warning=null},title={Text("部分尺寸尚未填写")},text={Text("有 ${j.items.sumOf {it.doc.segments.count {s->s.dimension==null}}} 条线未填写尺寸。导出时保留线条，不显示数字。")},confirmButton={TextButton({warning=null;startExport(j)}){Text("继续导出")}},dismissButton={TextButton({warning=null}){Text("返回修改")}})}
    vm.activeExport?.let {j->AlertDialog(onDismissRequest={if(vm.exporting) stopConfirm=true else vm.activeExport=null},title={Text(if(vm.exporting) "正在导出" else j.state)},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(vm.exporting) {LinearProgressIndicator(Modifier.fillMaxWidth());Text(vm.exportProgress)}
        if(j.album) Text("相册已保存 ${j.items.count {it.uri!=null}} / ${j.items.size} 张")
        if(j.pdf) Text(if(j.pdfDone) "PDF 已保存" else "PDF 尚未完成")
        j.error?.let {Text(it,color=MaterialTheme.colorScheme.error)};j.items.filter {it.error!=null}.forEach {Text("照片 ${it.number}：${it.error}",color=MaterialTheme.colorScheme.error)}
        if(!vm.exporting && j.pdfDone) TextButton({val intent=Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(j.pdfUri),"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);runCatching {ctx.startActivity(intent)}.onFailure {vm.error="没有可打开 PDF 的应用，可使用系统分享。"}}){Text("查看 PDF")}
        if(!vm.exporting && (j.pdfDone||j.items.any {it.uri!=null})) TextButton({val uris=ArrayList<Uri>();j.items.mapNotNull {it.uri}.forEach {uris+=Uri.parse(it)};if(j.pdfDone) uris+=Uri.parse(j.pdfUri);val send=Intent(Intent.ACTION_SEND_MULTIPLE).setType(if(j.pdfDone) "*/*" else "image/jpeg").putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);ctx.startActivity(Intent.createChooser(send,"分享量尺记录"))}){Text("系统分享")}
    }},confirmButton={TextButton({if(vm.exporting) stopConfirm=true else vm.activeExport=null}){Text(if(vm.exporting) "取消导出" else "关闭")}},dismissButton={if(!vm.exporting && j.state!="已完成") TextButton({startExport(j)}){Text("重试未完成项")}})}
    if(stopConfirm) Confirm("取消本次导出？","已完成的相册图片会保留，未完成项可稍后在导出记录中重试。",{stopConfirm=false}){vm.cancelExport()}
    if(history) ModalBottomSheet(onDismissRequest={history=false}) {LazyColumn(Modifier.fillMaxWidth().padding(24.dp)) {item {Text("导出记录",style=MaterialTheme.typography.titleLarge)};items(jobs,key={it.id}) {record->val j=vm.store.gson.fromJson(record.json,ExportJob::class.java);ListItem(headlineContent={Text(j.project)},supportingContent={Text("${j.state} · ${j.items.size} 张")},modifier=Modifier.clickable {vm.activeExport=j;history=false})}}}
    vm.busy?.let {AlertDialog(onDismissRequest={},title={Text("导入照片")},text={Column {LinearProgressIndicator(Modifier.fillMaxWidth());Spacer(Modifier.height(16.dp));Text(it)}},confirmButton={})}
    vm.error?.let {message->AlertDialog(onDismissRequest={vm.error=null},title={Text("需要留意")},text={Text(message)},confirmButton={TextButton({vm.error=null}){Text("知道了")}})}
}
