package com.lsama.measurephoto

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lsama.measurephoto.data.*
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.export.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppModel(app: Application): AndroidViewModel(app) {
    val store=Store(app)
    val projects=store.dao.projects().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val photos=store.dao.photos().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val jobs=store.dao.jobs().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf<String?>(null)
    var editing by mutableStateOf<Photo?>(null)
    var doc by mutableStateOf(Document()); private set
    private var history=History(doc)
    var pen by mutableStateOf(store.pen()); private set
    var autoDimension by mutableStateOf(store.autoDimension()); private set
    fun updateAutoDimension(enabled: Boolean) = action {
        withContext(Dispatchers.IO) {store.autoDimension(enabled)}
        autoDimension=enabled
    }
    var saveStatus by mutableStateOf("已保存");private set
    var selected by mutableStateOf<String?>(null)
    var historyVersion by mutableIntStateOf(0)
    val canUndo get()=history.canUndo;val canRedo get()=history.canRedo
    private val saveMutex=Mutex()
    private var saveGeneration=0
    private var savedGeneration=0
    private var noteJob: Job?=null
    var activeExport by mutableStateOf<ExportJob?>(null)
    var pendingExport by mutableStateOf<ExportJob?>(null)
    var exportProgress by mutableStateOf("")
    private var exportTask: Job?=null
    val exportEngine=ExportEngine(store)
    init {viewModelScope.launch(Dispatchers.IO) {runCatching {store.recover();exportEngine.recover()}.onFailure {withContext(Dispatchers.Main) {error="本地数据检查失败：${it.message}"}}}}
    fun action(block: suspend ()->Unit) {viewModelScope.launch {try {block()} catch(t: Exception) {error=t.message?:"操作失败"}}}
    fun open(p: Photo) {editing=p;doc=store.document(p);history=History(doc);historyVersion++;selected=null;saveGeneration=0;savedGeneration=0;saveStatus="已保存"}
    fun commit(next: Document) {if(history.commit(next)) {doc=history.current;historyVersion++;queueSave()}}
    fun undo() {doc=history.undo();historyVersion++;selected=selected?.takeIf {id->doc.segments.any {it.id==id}};queueSave()}
    fun redo() {doc=history.redo();historyVersion++;queueSave()}
    fun style(next: Pen) {val id=selected;if(id!=null) commit(doc.update(id){it.copy(pen=next)}) else {pen=next;action {withContext(Dispatchers.IO) {store.pen(next)}}}}
    fun room(value: String) {editing=editing?.copy(roomName=value.trim().take(30));queueSave()}
    fun note(value: String) {editing=editing?.copy(note=value.take(1000));saveStatus="保存中";noteJob?.cancel();noteJob=viewModelScope.launch {delay(500);queueSave()}}
    fun flush() {noteJob?.cancel();queueSave()}
    private fun queueSave() {
        val snapshot=editing?:return;val document=doc;val generation=++saveGeneration;saveStatus="保存中"
        viewModelScope.launch {
            saveMutex.withLock {
                try {store.save(snapshot,document);savedGeneration=generation;if(generation==saveGeneration && editing?.id==snapshot.id) saveStatus="已保存"}
                catch(t: Exception) {if(editing?.id==snapshot.id) {saveStatus="保存失败";error="保存失败，编辑仍保留。请重试：${t.message}"}}
            }
        }
    }
    fun leave(done: ()->Unit) {flush();viewModelScope.launch {saveMutex.withLock {if(savedGeneration==saveGeneration) {editing=null;done()} else error="尚未保存成功，请重试后再离开"}}}
    fun create(name: String,done: (String)->Unit) = action {val p=Project(name=name.trim());withContext(Dispatchers.IO) {store.dao.project(p)};done(p.id)}
    fun import(project: String,uris: List<Uri>,done: (Photo?)->Unit) = action {
        if(uris.isEmpty()) return@action
        busy="正在导入 0 / ${uris.size}";val imported=mutableListOf<Photo>();val failures=mutableListOf<String>()
        try {val start=(photos.value.filter {it.projectId==project}.maxOfOrNull {it.sortOrder}?:-1)+1
            uris.forEachIndexed {index,uri->try {imported+=store.addPhoto(project,uri,start+index)} catch(t: Exception) {failures+="第 ${index+1} 张：${t.message}"};busy="已处理 ${index+1} / ${uris.size}"}
            if(failures.isNotEmpty()) error="已导入 ${imported.size} 张\n"+failures.joinToString("\n")
            done(if(uris.size==1) imported.firstOrNull() else null)
        } finally {busy=null}
    }
    fun export(job: ExportJob) {
        if(exportTask?.isActive==true) return
        activeExport=job;exportProgress="准备导出"
        exportTask=viewModelScope.launch {try {exportEngine.run(job) {progress->viewModelScope.launch {exportProgress=progress}}}
            catch(t: CancellationException) {job.state="已取消"}
            catch(t: Exception) {job.state="失败";job.error=t.message?:"任务状态保存失败"}
            finally {activeExport=job.copy();exportProgress=job.state}}
    }
    val exporting get()=exportTask?.isActive==true
    fun cancelExport() {exportTask?.cancel()}
}
