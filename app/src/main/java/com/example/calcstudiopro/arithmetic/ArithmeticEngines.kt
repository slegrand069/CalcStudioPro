package com.example.calcstudiopro.arithmetic

import java.math.BigInteger

object ArithmeticLimits { const val MAX_FACTOR_BITS=48; const val MAX_SIEVE_N=2_000_000; const val MAX_FACTORIAL_N=1_000; const val MAX_NEIGHBOR_SEARCH=2_000_000; const val MAX_DIVISOR_LIST=2_000; const val MAX_CONGRUENCE_SOLUTIONS=100 }
object ArithmeticEngine {
 data class EuclideanDivision(val quotient:BigInteger,val remainder:BigInteger)
 data class Bezout(val gcd:BigInteger,val u:BigInteger,val v:BigInteger)
 fun gcd(a:BigInteger,b:BigInteger):BigInteger{var x=a.abs();var y=b.abs();while(y!=BigInteger.ZERO){val t=x.remainder(y);x=y;y=t};return x}
 fun lcm(a:BigInteger,b:BigInteger)=if(a==BigInteger.ZERO||b==BigInteger.ZERO)BigInteger.ZERO else a.divide(gcd(a,b)).multiply(b).abs()
 fun divide(a:BigInteger,b:BigInteger):EuclideanDivision{require(b!=BigInteger.ZERO){"Le diviseur doit être non nul."};val r=a.mod(b.abs());return EuclideanDivision(a.subtract(r).divide(b),r)}
 fun bezout(a:BigInteger,b:BigInteger):Bezout{if(a==BigInteger.ZERO&&b==BigInteger.ZERO)return Bezout(BigInteger.ZERO,BigInteger.ZERO,BigInteger.ZERO);var or=a.abs();var r=b.abs();var os=BigInteger.ONE;var s=BigInteger.ZERO;var ot=BigInteger.ZERO;var t=BigInteger.ONE;while(r!=BigInteger.ZERO){val q=or.divide(r);val nr=or-q*r;or=r;r=nr;val ns=os-q*s;os=s;s=ns;val nt=ot-q*t;ot=t;t=nt};return Bezout(or,if(a.signum()<0)-os else os,if(b.signum()<0)-ot else ot)}
 fun modulo(a:BigInteger,m:BigInteger):BigInteger{require(m.signum()>0);return a.mod(m)}
 fun divisorSumFromFactors(f:Map<BigInteger,Int>)=f.entries.fold(BigInteger.ONE){a,(p,e)->a.multiply(p.pow(e+1).subtract(BigInteger.ONE).divide(p.subtract(BigInteger.ONE)))}
 fun divisorCountFromFactors(f:Map<BigInteger,Int>)=f.values.fold(BigInteger.ONE){a,e->a.multiply(BigInteger.valueOf((e+1).toLong()))}
}
object PrimeEngine {
 fun isPrime(n:BigInteger)=n.signum()>0&&n.isProbablePrime(100)
 fun factor(n:BigInteger):Map<BigInteger,Int>{require(n>=BigInteger.ONE){"Entrez un entier positif."};require(n.bitLength()<=ArithmeticLimits.MAX_FACTOR_BITS){"Factorisation limitée aux entiers de moins de ${ArithmeticLimits.MAX_FACTOR_BITS+1} bits."};var x=n;val out=linkedMapOf<BigInteger,Int>();var p=BigInteger.TWO;while(p*p<=x){var e=0;while(x.mod(p)==BigInteger.ZERO){x=x.divide(p);e++};if(e>0)out[p]=e;p=if(p==BigInteger.TWO)BigInteger.valueOf(3) else p+BigInteger.TWO};if(x>BigInteger.ONE)out[x]=(out[x]?:0)+1;return out}
 fun primesTo(n:Int):List<Int>{require(n in 0..ArithmeticLimits.MAX_SIEVE_N){"La borne maximale est ${ArithmeticLimits.MAX_SIEVE_N}."};if(n<2)return emptyList();val c=BooleanArray(n+1);var p=2;while(p.toLong()*p<=n){if(!c[p]){var k=p*p;while(k<=n){c[k]=true;k+=p}};p++};return(2..n).filter{!c[it]}}
 fun neighbors(n:BigInteger):Pair<BigInteger?,BigInteger>{require(n.abs().bitLength()<ArithmeticLimits.MAX_FACTOR_BITS){"Recherche limitée aux entiers de moins de ${ArithmeticLimits.MAX_FACTOR_BITS} bits."};var steps=0;var lo=n-BigInteger.ONE;while(lo>=BigInteger.TWO&&!isPrime(lo)){lo-=BigInteger.ONE;require(++steps<=ArithmeticLimits.MAX_NEIGHBOR_SEARCH){"Limite de recherche atteinte."}};steps=0;var hi=n+BigInteger.ONE;while(!isPrime(hi)){hi+=BigInteger.ONE;require(++steps<=ArithmeticLimits.MAX_NEIGHBOR_SEARCH){"Limite de recherche atteinte."}};return lo.takeIf{it>=BigInteger.TWO} to hi}
}
object CombinatoricsEngine {
 fun factorial(n:Int):BigInteger{require(n in 0..ArithmeticLimits.MAX_FACTORIAL_N){"n doit être compris entre 0 et ${ArithmeticLimits.MAX_FACTORIAL_N}."};var x=BigInteger.ONE;for(i in 2..n)x*=BigInteger.valueOf(i.toLong());return x}
 fun arrangements(n:Int,r:Int):BigInteger{require(n>=0&&r in 0..n);var x=BigInteger.ONE;for(i in 0 until r)x*=BigInteger.valueOf((n-i).toLong());return x}
 fun combinations(n:Int,r:Int):BigInteger{require(n>=0&&r in 0..n);val k=minOf(r,n-r);var x=BigInteger.ONE;for(i in 1..k)x=x.multiply(BigInteger.valueOf((n-k+i).toLong())).divide(BigInteger.valueOf(i.toLong()));return x}
 fun repetitions(n:Int,r:Int):BigInteger{require(n>=0&&r in 0..10_000);return BigInteger.valueOf(n.toLong()).pow(r)}
 fun multiset(counts:List<Int>):BigInteger{require(counts.isNotEmpty()&&counts.all{it>=0});val total=counts.sum();var x=factorial(total);counts.forEach{x=x.divide(factorial(it))};return x}
}
object NumberSequencesEngine {
 fun fibonacci(n:Int):BigInteger{require(n>=0);fun pair(k:Int):Pair<BigInteger,BigInteger>{if(k==0)return BigInteger.ZERO to BigInteger.ONE;val(a,b)=pair(k/2);val c=a*(b.shiftLeft(1)-a);val d=a*a+b*b;return if(k%2==0)c to d else d to c+d};return pair(n).first}
 fun triangular(n:BigInteger)=n*(n+BigInteger.ONE)/BigInteger.TWO
 fun isSquare(n:BigInteger)=n.signum()>=0&&n.sqrt().let{it*it==n}
 fun integerCubeRoot(n:BigInteger):BigInteger{require(n.signum()>=0);if(n<BigInteger.TWO)return n;var x=BigInteger.ONE.shiftLeft((n.bitLength()+2)/3);while(true){val y=(x*BigInteger.TWO+n/(x*x))/BigInteger.valueOf(3);if(y>=x)return x;x=y}}
 fun isCube(n:BigInteger):Boolean{if(n.signum()<0)return isCube(n.negate());val r=integerCubeRoot(n);return r*r*r==n}
 fun digitSum(n:BigInteger)=BigInteger.valueOf(n.abs().toString().sumOf{it.digitToInt()}.toLong())
 fun digitalRoot(n:BigInteger)=if(n==BigInteger.ZERO)BigInteger.ZERO else BigInteger.valueOf(((n.abs()-BigInteger.ONE).mod(BigInteger.valueOf(9))+BigInteger.ONE).toLong())
}
fun divisorsFromFactors(factors:Map<BigInteger,Int>,limit:Int=ArithmeticLimits.MAX_DIVISOR_LIST):List<BigInteger>?{if(ArithmeticEngine.divisorCountFromFactors(factors)>BigInteger.valueOf(limit.toLong()))return null;var values=listOf(BigInteger.ONE);factors.forEach{(p,e)->var power=BigInteger.ONE;val powers=(1..e).map{power*=p;power};values=values.flatMap{base->listOf(base)+powers.map{base*it}}};return values.sorted()}
