package com.lsama.measurephoto.data

import android.content.Context
import android.net.Uri
import androidx.room.*
import com.google.gson.Gson
import com.lsama.measurephoto.model.*
import com.lsama.measurephoto.render.Photos
import com.lsama.measurephoto.render.Renderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

@Entity data class Project(@PrimaryKey val id: String=uid(),val name: String,val createdAt: Long=System.currentTimeMillis(),val updatedAt: Long=System.currentTimeMillis())
@Entity(indices=[Index("projectId")],foreignKeys=[ForeignKey(entity=Project::class,parentColumns=["id"],childColumns=["projectId"],onDelete=ForeignKey.CASCADE)])
data class Photo(@PrimaryKey val id: String=uid(),val projectId: String,val originalPath: String,val thumbnailPath: String,
    val orientedWidth: Int,val orientedHeight: Int,val exifOrientation: Int=1,val roomName: String="",val note: String="",
    val sortOrder: Int=0,val createdAt: Long=System.currentTimeMillis(),val updatedAt: Long=System.currentTimeMillis(),val document: String="")
@Entity data class ExportRecord(@PrimaryKey val id: String,val json: String,val updatedAt: Long=System.currentTimeMillis())
@androidx.room.Dao interface Dao {
    @Query("SELECT * FROM Project ORDER BY updatedAt DESC") fun projects(): Flow<List<Project>>
    @Query("SELECT * FROM Photo ORDER BY sortOrder, createdAt") fun photos(): Flow<List<Photo>>
    @Query("SELECT * FROM ExportRecord ORDER BY updatedAt DESC") fun jobs(): Flow<List<ExportRecord>>
    @Upsert suspend fun project(p: Project)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun photo(p: Photo)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun job(j: ExportRecord)
    @Query("UPDATE Project SET updatedAt=:time WHERE id=:id") suspend fun touch(id: String,time: Long)
    @Query("DELETE FROM Project WHERE id=:id") suspend fun deleteProject(id: String)
    @Query("DELETE FROM Photo WHERE id IN (:ids)") suspend fun deletePhotos(ids: List<String>)
    @Query("SELECT * FROM Photo") suspend fun allPhotos(): List<Photo>
    @Query("SELECT * FROM ExportRecord") suspend fun allJobs(): List<ExportRecord>
    @Query("DELETE FROM ExportRecord WHERE id=:id") suspend fun deleteJob(id: String)
    @Transaction suspend fun save(p: Photo) { photo(p);touch(p.projectId,System.currentTimeMillis()) }
    @Transaction suspend fun order(list: List<Photo>) { list.forEachIndexed { i,p->photo(p.copy(sortOrder=i)) };list.firstOrNull()?.let { touch(it.projectId,System.currentTimeMillis()) } }
}
@androidx.room.Database(entities=[Project::class,Photo::class,ExportRecord::class],version=1,exportSchema=true)
abstract class Database: RoomDatabase() { abstract fun dao(): Dao }
class Store(val context: Context) {
    companion object { private val fileMutex=Mutex() }
    val db=Room.databaseBuilder(context,Database::class.java,"measure.db").build()
    val dao=db.dao(); val gson=Gson()
    val media=File(context.filesDir,"photos").apply { mkdirs() }
    fun document(p: Photo): Document = if(p.document.isBlank()) Document() else gson.fromJson(p.document,Document::class.java).also { require(it.schemaVersion==1) { "不支持此标注版本" } }
    suspend fun addPhoto(projectId: String,uri: Uri,order: Int): Photo = withContext(Dispatchers.IO) { fileMutex.withLock {
        val id=uid(); val original=File(media,"$id.original");val thumb=File(media,"$id.jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input-> original.outputStream().use { input.copyTo(it) } } ?: error("无法读取所选图片")
            val info=Photos.info(original.path)
            val p=Photo(id,projectId,original.path,thumb.path,info.first,info.second,info.third,sortOrder=order)
            makeThumbnail(p,Document())
            dao.save(p)
            p
        } catch(t: Throwable) { original.delete();thumb.delete();throw t }
    }}
    suspend fun save(p: Photo,doc: Document): Photo = withContext(Dispatchers.IO) { fileMutex.withLock {
        val next=p.copy(document=gson.toJson(doc),updatedAt=System.currentTimeMillis())
        // Render before publishing the new revision so observers cannot decode a stale thumbnail.
        makeThumbnail(next,doc)
        dao.save(next)
        next
    }}
    private fun makeThumbnail(p: Photo,doc: Document) {
        val bitmap=Photos.decode(p.originalPath,640)
        try { Renderer.annotations(android.graphics.Canvas(bitmap),doc,bitmap.width.toFloat(),bitmap.height.toFloat());
            val temp=File(p.thumbnailPath+".tmp");temp.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,it)) };check(temp.renameTo(File(p.thumbnailPath)))
        } finally { bitmap.recycle() }
    }
    suspend fun deletePhotos(list: List<Photo>) = withContext(Dispatchers.IO) { fileMutex.withLock {
        dao.deletePhotos(list.map { it.id });list.forEach { File(it.originalPath).delete();File(it.thumbnailPath).delete() }
    }}
    suspend fun deleteProject(p: Project) = withContext(Dispatchers.IO) { fileMutex.withLock {
        val list=dao.allPhotos().filter { it.projectId==p.id };dao.deleteProject(p.id)
        list.forEach { File(it.originalPath).delete();File(it.thumbnailPath).delete() }
    }}
    suspend fun recover() = withContext(Dispatchers.IO) { fileMutex.withLock {
        // Only files owned by this app in its own photo directory can be reclaimed.
        val referenced=dao.allPhotos().flatMap { listOf(it.originalPath,it.thumbnailPath) }.toSet()
        media.listFiles()?.filter { it.path !in referenced }?.forEach { it.delete() }
    }}
    private val prefs=context.getSharedPreferences("editor",Context.MODE_PRIVATE)
    fun autoDimension()=prefs.getBoolean("autoDimension",true)
    fun autoDimension(enabled: Boolean) {check(prefs.edit().putBoolean("autoDimension",enabled).commit()) {"无法保存输入设置"}}
    fun pen()=Pen(prefs.getInt("color",0xFFC23D31.toInt()),prefs.getFloat("width",3f),prefs.getFloat("font",20f))
    fun pen(p: Pen) { check(prefs.edit().putInt("color",p.color).putFloat("width",p.width).putFloat("font",p.font).commit()) { "无法保存默认样式" } }
}
