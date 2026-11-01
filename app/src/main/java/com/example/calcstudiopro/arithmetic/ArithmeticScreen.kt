package com.example.calcstudiopro.arithmetic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlin.math.max

@Composable fun ArithmeticRoute(vm:ArithmeticViewModel,onOpenModeSelector:()->Unit){val s by vm.state.collectAsState();ArithmeticScreen(s,onOpenModeSelector,vm::selectFamily,vm::selectFunction,vm::edit,vm::calculateExpensive,vm::save)}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ArithmeticScreen(state:ArithmeticState,onMode:()->Unit,onFamily:(Int)->Unit,onFunction:(Int)->Unit,onEdit:(Int,String)->Unit,onCalculate:()->Unit,onSave:()->Unit){
 Scaffold(topBar={TopAppBar(title={Text("Arithmétique")},actions={TextButton(onClick=onMode,modifier=Modifier.testTag("arithmetic-mode")){Text("MODE")}})}){pad->Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 DropdownChoice("Famille",arithmeticFamilies,state.family,"arithmetic-family",onFamily)
 DropdownChoice("Fonction",arithmeticFunctions[state.family],state.function,"arithmetic-function",onFunction)
 val labels=when(state.family){0->listOf("a","b");1->if(state.function==4)listOf("De","À") else listOf("n");2->if(state.function==4)listOf("Effectifs (ex. 2, 2, 1)") else if(state.function==0)listOf("n") else listOf("n","r");3->if(state.function==1||state.function==3)listOf("a","e ou b","m") else listOf("a","m");else->if(state.function==3)listOf("a","b") else listOf("n")}
 labels.forEachIndexed{i,label->val isDisabled=state.family==0&&state.function==4&&i==1;val isMultiset=state.family==2&&state.function==4;OutlinedTextField(value=listOf(state.a,state.b,state.c)[i],onValueChange={onEdit(i,it)},enabled=!isDisabled,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(isMultiset)KeyboardType.Text else KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("arithmetic-input-$i"),supportingText={if(isMultiset)Text("Saisissez les effectifs séparés par des virgules")})}
 if(state.family==0&&state.function==4||state.family==1||state.family==4&&state.function in 2..3)Button(onClick=onCalculate,enabled=!state.busy,modifier=Modifier.fillMaxWidth().testTag("arithmetic-calculate")){Text(if(state.busy)"Calcul en cours…" else "Calculer")}
 state.error?.let{Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("arithmetic-error"))}
 if(state.result.isNotBlank()){ElevatedCard(Modifier.fillMaxWidth().testTag("arithmetic-result")){Text(state.result,Modifier.padding(16.dp),style=MaterialTheme.typography.titleMedium)};state.tree?.let{FactorTreeCanvas(it,Modifier.fillMaxWidth().testTag("factor-tree"))};if(state.family==1&&state.function==2){val n=state.a.toBigIntegerOrNull();if(n!=null)runCatching{PrimeEngine.factor(n)}.getOrNull()?.let{Text("${n} = "+it.entries.joinToString(" × "){(p,e)->if(e==1)"$p" else "$p^$e"})}}}
 if(state.saved)Text("Enregistré dans l’historique.",color=MaterialTheme.colorScheme.primary)
 Button(onClick=onSave,enabled=state.result.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("arithmetic-save")){Text("Enregistrer dans l’historique")}
 }}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DropdownChoice(label:String,items:List<String>,selected:Int,tag:String,onSelect:(Int)->Unit){var expanded by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded=expanded,onExpandedChange={expanded=it}){OutlinedTextField(value=items[selected],onValueChange={},readOnly=true,label={Text(label)},modifier=Modifier.menuAnchor().fillMaxWidth().testTag(tag));ExposedDropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){items.forEachIndexed{i,v->DropdownMenuItem(text={Text(v)},onClick={onSelect(i);expanded=false})}}}}

@Composable fun FactorTreeCanvas(root:FactorTreeNode,modifier:Modifier=Modifier){
 val layout=remember(root){FactorTreeLayouter.layout(root)}
 val density=androidx.compose.ui.platform.LocalDensity.current
 val contentWidth=with(density){layout.width.dp}
 val contentHeight=with(density){layout.height.dp}
 val primary=MaterialTheme.colorScheme.primary;val leaf=MaterialTheme.colorScheme.tertiary;val text=MaterialTheme.colorScheme.onSurface;val surface=MaterialTheme.colorScheme.surface
 Row(modifier.horizontalScroll(rememberScrollState())) {
  Canvas(Modifier.width(contentWidth).height(contentHeight)) {
   val scale=density.density
   fun point(p:FactorTreePoint)=Offset(p.x*scale,p.y*scale)
   layout.edges.forEach { edge ->
    val parent=point(edge.parent);val child=point(edge.child);val dx=child.x-parent.x;val dy=child.y-parent.y;val distance=kotlin.math.sqrt(dx*dx+dy*dy).coerceAtLeast(1f)
    val start=Offset(parent.x+dx/distance*edge.parent.radius*scale,parent.y+dy/distance*edge.parent.radius*scale)
    val end=Offset(child.x-dx/distance*edge.child.radius*scale,child.y-dy/distance*edge.child.radius*scale)
    drawLine(primary,start,end,1.5.dp.toPx())
   }
   layout.nodes.forEach { item ->
    val center=point(item);val radius=item.radius*scale
    drawCircle(if(item.node.prime)leaf else primary,radius,center)
    drawIntoCanvas { canvas ->
     val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=surface.toArgb();textAlign=android.graphics.Paint.Align.CENTER;textSize=11.dp.toPx();typeface=android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD)}
     canvas.nativeCanvas.drawText(item.node.value.toString(),center.x,center.y-(paint.ascent()+paint.descent())/2f,paint)
    }
   }
  }
 }
}
