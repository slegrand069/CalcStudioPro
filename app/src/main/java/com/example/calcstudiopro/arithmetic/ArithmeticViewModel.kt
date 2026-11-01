package com.example.calcstudiopro.arithmetic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.calcstudiopro.history.domain.HistoryModes
import com.example.calcstudiopro.history.domain.HistoryRepository
import com.example.calcstudiopro.history.domain.NewHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

val arithmeticFamilies = listOf("Divisibilité","Nombres premiers","Combinatoire","Arithmétique modulaire","Suites et curiosités")
val arithmeticFunctions = listOf(
 listOf("PGCD","PPCM","Division euclidienne","Bézout","Diviseurs","Test de divisibilité"),
 listOf("Test de primalité","Factorisation première","Arbre de factorisation","Premiers voisins","Liste des nombres premiers"),
 listOf("Factorielle","Arrangements sans répétition","Combinaisons","Arrangements avec répétition","Permutations d'un multiensemble"),
 listOf("Modulo","Puissance modulaire","Inverse modulaire","Congruence linéaire"),
 listOf("Fibonacci","Nombres figurés","Nombre parfait","Nombres amicaux","Somme des chiffres","Racine numérique","Propriétés")
)
class FactorTreeNode(val value: BigInteger, val prime: Boolean, val left: FactorTreeNode?=null, val right: FactorTreeNode?=null)
fun buildFactorTree(n:BigInteger,factors:Map<BigInteger,Int>):FactorTreeNode { val leaves=factors.flatMap{(p,e)->List(e){p}}; fun build(from:Int,to:Int):FactorTreeNode {if(to-from==1)return FactorTreeNode(leaves[from],true);val mid=(from+to)/2;val l=build(from,mid);val r=build(mid,to);return FactorTreeNode(l.value*r.value,false,l,r)};return if(leaves.size==1)FactorTreeNode(n,true) else build(0,leaves.size) }
data class ArithmeticState(val family:Int=0,val function:Int=0,val a:String="",val b:String="",val c:String="",val result:String="",val error:String?=null,val busy:Boolean=false,val tree:FactorTreeNode?=null,val saved:Boolean=false)
class ArithmeticViewModel(private val history:HistoryRepository?=null):ViewModel(){
 private var job:Job?=null
 private var version=0
 private val flow=kotlinx.coroutines.flow.MutableStateFlow(ArithmeticState())
 val state=flow
 fun selectFamily(i:Int){job?.cancel();version++;flow.value=ArithmeticState(family=i)}
 fun selectFunction(i:Int){job?.cancel();version++;flow.value=ArithmeticState(family=flow.value.family,function=i)}
 fun edit(which:Int,text:String){version++;job?.cancel();val s=flow.value;val n=when(which){0->s.copy(a=text);1->s.copy(b=text);else->s.copy(c=text)}.copy(result="",error=null,tree=null,saved=false);flow.value=n; calculate(n,version)}
 private fun calculate(s:ArithmeticState, token:Int){
  val f=arithmeticFunctions[s.family][s.function]; val isExpensive=(s.family==0&&s.function==4)||(s.family==1&&s.function in 0..4)||(s.family==4&&s.function in 2..3)
  if(s.family==2&&s.function==4){
   if(s.a.isBlank())return
   val parsed=runCatching{s.a.trim().split(Regex("[,;\\s]+")).filter(String::isNotBlank).map(String::toInt)}
   parsed.onSuccess{counts->runCatching{CombinatoricsEngine.multiset(counts)}.onSuccess{flow.value=s.copy(result="Permutations du multiensemble = $it",error=null)}.onFailure{flow.value=s.copy(error=it.message?:"Effectifs invalides")}}
    .onFailure{flow.value=s.copy(error="Saisissez des effectifs entiers non négatifs.")}
   return
  }
  fun bi(t:String)=t.trim().takeIf{it.matches(Regex("[+-]?\\d+"))}?.let(::BigInteger)
  val a=bi(s.a);val b=bi(s.b);val c=bi(s.c)
  if(isExpensive){flow.value=s.copy(busy=false);return}
  val needed=when(s.family){0->if(s.function in 0..3||s.function==5)2 else 1;1->if(s.function==4)2 else 1;2->if(s.function==0)1 else 2;3->if(s.function==1||s.function==3)3 else 2;4->if(s.function==3)2 else 1;else->1}
  if(a==null||(needed>=2&&b==null)||(needed>=3&&c==null))return
  val result=runCatching { when(s.family){
   0->when(s.function){0->"PGCD(${a},${b}) = ${ArithmeticEngine.gcd(a,requireNotNull(b))}";1->"PPCM(${a},${b}) = ${ArithmeticEngine.lcm(a,requireNotNull(b))}";2->{val d=ArithmeticEngine.divide(a,b!!);"q = ${d.quotient}, r = ${d.remainder} ; ${a} = ${b} × ${d.quotient} + ${d.remainder}"};3->{val z=ArithmeticEngine.bezout(a,b!!);"${a} × ${z.u} + ${b} × ${z.v} = ${z.gcd}"};4->"Calculer";else->{val divisor=requireNotNull(b);require(divisor!=BigInteger.ZERO);"${if(a.mod(divisor.abs())==BigInteger.ZERO)"Oui" else "Non"} ; reste ${a.mod(divisor.abs())}"}}
   2->when(s.function){0->{require(a.signum()>=0);"${a}! = ${CombinatoricsEngine.factorial(a.intValueExact())}"};1->{"${a}P${b} = ${CombinatoricsEngine.arrangements(a.intValueExact(),b!!.intValueExact())}"};2->{"C(${a},${b}) = ${CombinatoricsEngine.combinations(a.intValueExact(),b!!.intValueExact())}"};3->{"${a}^${b} = ${CombinatoricsEngine.repetitions(a.intValueExact(),b!!.intValueExact())}"};else->{val counts=(s.a.trim().split(Regex("[ ,;]+"))).map{it.toInt()};"Permutations = ${CombinatoricsEngine.multiset(counts)}"}}
   3->when(s.function){0->{require(b!!.signum()>0);"${a}.mod(${b}) = ${a.mod(b)}"};1->{require(b!!.signum()>=0&&c!!.signum()>0);"${a}^${b} mod ${c} = ${a.modPow(b,c)}"};2->{require(b!!.compareTo(BigInteger.ONE)>0);"Inverse = ${a.modInverse(b)}"};else->{require(c!!.signum()>0);val d=ArithmeticEngine.gcd(a,c);if(b!!.mod(d)!=BigInteger.ZERO)"Aucune solution" else {val m=c.divide(d);val x=ArithmeticEngine.bezout(a.divide(d),m).u.multiply(b.divide(d)).mod(m);"x ≡ $x (mod $m) ; ${d} classe(s) modulo $c"}}}
   4->when(s.function){0->{require(a.signum()>=0);"Fibonacci(${a}) = ${NumberSequencesEngine.fibonacci(a.intValueExact())}"};1->{require(a.signum()>=0);"Triangulaire ${NumberSequencesEngine.triangular(a)}, carré ${a*a}, cube ${a*a*a}"};5->"Somme des chiffres = ${NumberSequencesEngine.digitSum(a)} ; racine numérique = ${NumberSequencesEngine.digitalRoot(a)}";6->"${if(a.mod(BigInteger.TWO)==BigInteger.ZERO)"Pair" else "Impair"} ; carré parfait: ${NumberSequencesEngine.isSquare(a)} ; cube parfait: ${NumberSequencesEngine.isCube(a)}";else->"Somme des diviseurs propres requiert la factorisation"}
   1->"Calculer";else->"Calcul explicite requis"
  }}
  result.onSuccess{flow.value=flow.value.copy(result=it,error=null)}.onFailure{flow.value=flow.value.copy(error=it.message?:"Entrée invalide")}
 }
 fun calculateExpensive(){
  val s=flow.value; val token=++version; job?.cancel(); flow.value=s.copy(busy=true,result="",error=null)
  job=viewModelScope.launch {
   val out=withContext(Dispatchers.Default){runCatching {
    fun factor(n:BigInteger):Map<BigInteger,Int> = PrimeEngine.factor(n)
    val n=s.a.trim().toBigInteger()
    when(s.family){
     0->{require(n.signum()>0);val f=factor(n);val count=ArithmeticEngine.divisorCountFromFactors(f);val sum=ArithmeticEngine.divisorSumFromFactors(f);val ds=divisorsFromFactors(f);"${count} diviseurs ; somme = $sum ; ${ds?.joinToString(", ")?:"liste omise (plus de ${ArithmeticLimits.MAX_DIVISOR_LIST} diviseurs)"}"}
     1->when(s.function){0->"${n} : ${if(n<BigInteger.TWO)"ni premier ni composé" else if(PrimeEngine.isPrime(n))"probablement premier (certitude 100)" else "composé"}";1,2->{require(n>=BigInteger.TWO){"Entrez un entier supérieur ou égal à 2."};val f=factor(n);val compact=f.entries.joinToString(" × "){(p,e)->if(e==1)"$p" else "$p^$e"};val tree=buildFactorTree(n,f);compact to tree};3->{val(p,q)=PrimeEngine.neighbors(n);"Premier inférieur : ${p?:"aucun"} ; supérieur : $q"};else->{val upper=s.b.trim().toInt();val lower=n.intValueExact();require(lower>=0&&upper>=lower){"La borne de départ doit être positive et inférieure ou égale à la borne de fin."};val ps=PrimeEngine.primesTo(upper).filter{it>=lower};"${ps.size} nombres premiers de $lower à $upper : ${ps.joinToString(", ")}"}}
     4->if(s.function==2){require(n.signum()>0);"Somme propre = ${ArithmeticEngine.divisorSumFromFactors(factor(n))-n} : ${if(ArithmeticEngine.divisorSumFromFactors(factor(n))-n==n)"parfait" else "non parfait"}"}else{val b=s.b.trim().toBigInteger();require(n.signum()>0&&b.signum()>0&&n!=b);val sa=ArithmeticEngine.divisorSumFromFactors(factor(n))-n;val sb=ArithmeticEngine.divisorSumFromFactors(factor(b))-b;"s(a)=$sa, s(b)=$sb : ${if(sa==b&&sb==n)"amicaux" else "non amicaux"}"}
     else->"Calcul non disponible"
    }
   }}
   if(token==version)out.onSuccess{val value=if(it is Pair<*,*>){flow.value=flow.value.copy(result=it.first.toString(),tree=it.second as FactorTreeNode,busy=false);null}else it.toString();if(value!=null)flow.value=flow.value.copy(result=value,busy=false)}.onFailure{flow.value=flow.value.copy(error=it.message?:"Calcul impossible",busy=false)}
  }
 }
 fun save(){val s=flow.value;if(s.result.isBlank())return;val repo=history?:return;val fn=arithmeticFunctions[s.family][s.function];val description="${arithmeticFamilies[s.family]} — $fn (${s.a}${if(s.b.isNotBlank())", ${s.b}" else ""}${if(s.c.isNotBlank())", ${s.c}" else ""})";viewModelScope.launch{runCatching{repo.add(NewHistoryEntry(HistoryModes.ARITHMETIC,description,s.result,null,null,detailsJson="""{"version":1,"family":${s.family},"function":${s.function},"inputs":["${s.a}","${s.b}","${s.c}"],"result":"${s.result.replace("\\","\\\\").replace("\"","\\\"")}"}"""))}.onSuccess{flow.value=flow.value.copy(saved=true)}.onFailure{flow.value=flow.value.copy(error=it.message?:"Enregistrement impossible")}}
 }
 class Factory(private val repo:HistoryRepository):ViewModelProvider.Factory{@Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>):T{require(modelClass.isAssignableFrom(ArithmeticViewModel::class.java));return ArithmeticViewModel(repo) as T}}
}



