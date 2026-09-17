package com.lsama.measurephoto.ui

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

@Composable fun CameraScreen(onBack: ()->Unit,onCaptured: (File)->Unit,onError: (String)->Unit) {
    val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current
    val preview=remember {PreviewView(ctx).apply {scaleType=PreviewView.ScaleType.FIT_CENTER}}
    val capture=remember {ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()}
    var ready by remember {mutableStateOf(false)};var saving by remember {mutableStateOf(false)}
    DisposableEffect(owner) {
        var disposed=false;var provider: ProcessCameraProvider?=null
        val future=ProcessCameraProvider.getInstance(ctx)
        future.addListener({if(!disposed) try {provider=future.get();val p=Preview.Builder().build().also {it.surfaceProvider=preview.surfaceProvider};provider?.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,p,capture);ready=true} catch(t: Exception) {onError("相机无法启动：${t.message}")}},ContextCompat.getMainExecutor(ctx))
        onDispose {disposed=true;provider?.unbindAll()}
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row {Tool(Icons.Outlined.ArrowBack,"返回",onClick=onBack);Text("拍摄现场",Modifier.padding(12.dp))}
        AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(24.dp),horizontalArrangement=Arrangement.Center) {Primary(if(saving) "正在保存" else "拍照",Icons.Outlined.PhotoCamera,enabled=ready&&!saving) {
            saving=true;val file=File.createTempFile("camera-",".jpg",ctx.cacheDir)
            capture.targetRotation=preview.display?.rotation?:android.view.Surface.ROTATION_0
            capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),ContextCompat.getMainExecutor(ctx),object: ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {saving=false;onCaptured(file)}
                override fun onError(exception: ImageCaptureException) {saving=false;file.delete();onError("拍照失败：${exception.message}")}
            })
        }}
    }
}
