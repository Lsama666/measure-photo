package com.lsama.measurephoto.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink=Color(0xFF232725)
val Quiet=Color(0xFF65706B)
val Green=Color(0xFF2F655C)
val Paper=Color(0xFFF6F6F3)
val Rule=Color(0xFFE4E7E2)
@Composable fun MeasureTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Green,onPrimary=Color.White,primaryContainer=Color(0xFFE4EEE8),onPrimaryContainer=Green,
        background=Paper,onBackground=Ink,surface=Color.White,onSurface=Ink,surfaceVariant=Paper,onSurfaceVariant=Quiet,outlineVariant=Rule,error=Color(0xFFB5362D)),
        shapes=Shapes(extraSmall=RoundedCornerShape(6.dp),small=RoundedCornerShape(8.dp),medium=RoundedCornerShape(12.dp),large=RoundedCornerShape(16.dp)),
        typography=Typography(titleLarge=androidx.compose.ui.text.TextStyle(fontSize=22.sp,fontWeight=FontWeight.Medium),bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp)),content=content)
}
@Composable fun Tool(icon: ImageVector,label: String,enabled: Boolean=true,selected: Boolean=false,onClick: ()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)) {Icon(icon,label,tint=if(!enabled) Quiet.copy(alpha=.35f) else if(selected) Green else Ink)}
}
@Composable fun Primary(label: String,icon: ImageVector?=null,modifier: Modifier=Modifier,enabled: Boolean=true,onClick: ()->Unit) {
    Button(onClick,modifier.heightIn(min=48.dp),enabled=enabled,shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) {if(icon!=null) {Icon(icon,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp))};Text(label)}
}
