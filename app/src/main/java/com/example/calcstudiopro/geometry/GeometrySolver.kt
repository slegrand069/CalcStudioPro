package com.example.calcstudiopro.geometry

import kotlin.math.*

enum class GeometryFigure(val label: String) { TRIANGLE("Triangle"), CIRCLE("Cercle"), PARALLELOGRAM("Parallélogramme") }
enum class GeometryVariant(val label: String) {
    GENERAL("Quelconque"), RIGHT("Rectangle"), ISOSCELES("Isocèle"), EQUILATERAL("Équilatéral"),
    FULL_CIRCLE("Cercle complet"), ARC_SECTOR("Arc et secteur"), MINOR_SEGMENT("Segment mineur"), MAJOR_SEGMENT("Segment majeur"),
    RECTANGLE("Rectangle"), RHOMBUS("Losange"), SQUARE("Carré")
}

data class GeometryProblem(
    val figure: GeometryFigure,
    val variant: GeometryVariant = GeometryVariant.GENERAL,
    val values: Map<String, Double>,
    val radians: Boolean = false
)

data class GeometrySolution(
    val values: Map<String, Double>,
    val formulas: List<String>,
    val triangle: TriangleEngine.Result? = null,
    val formulaIds: List<String> = formulas.map(::formulaId),
    val note: String? = null
)

private fun formulaId(expression: String): String = when (expression) {
    "Loi des sinus" -> "triangle.law_of_sines"
    "Loi des cosinus" -> "triangle.law_of_cosines"
    "Formule de Héron" -> "triangle.heron"
    "S = ab sin(C)/2" -> "triangle.area_sas_c"
    "S = bc sin(A)/2" -> "triangle.area_sas_a"
    "S = ca sin(B)/2" -> "triangle.area_sas_b"
    "a² + b² = c²" -> "triangle.pythagorean"
    "A + B = 90°" -> "triangle.right_angle_sum"
    "Somme des angles" -> "triangle.angle_sum"
    "a = b = c" -> "triangle.equilateral_sides"
    "A = B = C = 60°" -> "triangle.equilateral_angles"
    "P = a + b + c" -> "triangle.perimeter"
    "hₐ = 2S/a" -> "triangle.height_a"
    "hᵦ = 2S/b" -> "triangle.height_b"
    "h_b = 2S/b" -> "triangle.height_b"
    "h꜀ = 2S/c" -> "triangle.height_c"
    "r = S/s" -> "triangle.inradius"
    "R = abc/(4S)" -> "triangle.circumradius"
    "sin(A) = a/c" -> "triangle.right_sine"
    "sin(B) = b/c" -> "triangle.right_sine"
    "cos(A) = b/c" -> "triangle.right_cosine"
    "cos(B) = a/c" -> "triangle.right_cosine"
    "a = b" -> "triangle.isosceles_sides"
    "d = 2r" -> "circle.diameter"
    "C = 2πr" -> "circle.circumference"
    "P = 2πr" -> "circle.circumference"
    "S = πr²" -> "circle.area"
    "s = rθ" -> "circle.arc_length"
    "A = r²θ/2" -> "circle.sector_area"
    "c = 2r sin(θ/2)" -> "circle.chord_length"
    "h = r(1 − cos(θ/2))" -> "circle.sagitta"
    "A = r²(θ − sin θ)/2" -> "circle.segment_area"
    "S segment = S cercle − S mineur" -> "circle.major_segment_area"
    "S segment = r²(θ − sin θ)/2" -> "circle.segment_area"
    "S = ab sin(θ)" -> "parallelogram.area"
    "d₁² = a² + b² + 2ab cos(θ)" -> "parallelogram.diagonal_plus"
    "d₂² = a² + b² − 2ab cos(θ)" -> "parallelogram.diagonal_minus"
    "P = 2(a+b)" -> "parallelogram.perimeter"
    "S = ab" -> "rectangle.area"
    "d² = a²+b²" -> "rectangle.diagonal"
    "d = a√2" -> "square.diagonal"
    "S = a²" -> "square.area"
    "P = 4a" -> "square.perimeter"
    "hₐ = S/a" -> "parallelogram.height_a"
    "hᵦ = S/b" -> "parallelogram.height_b"
    "h_b = S/b" -> "parallelogram.height_b"
    else -> "geometry.formula.${expression.hashCode().toUInt().toString(16)}"
}

sealed interface GeometrySolveResult {
    data class Solved(val solutions: List<GeometrySolution>) : GeometrySolveResult
    data object Insufficient : GeometrySolveResult
    data class Unsupported(val message: String) : GeometrySolveResult
    data class Impossible(val message: String) : GeometrySolveResult
    data class Indeterminate(val message: String) : GeometrySolveResult
}

/** Pure, explicit constraint solvers. Unknown entries are omitted from [GeometryProblem.values]. */
object GeometrySolver {
    private const val EPS = 1e-9
    private fun close(a: Double, b: Double, tolerance: Double = 1e-8) =
        abs(a - b) <= tolerance * max(Double.MIN_VALUE, max(abs(a), abs(b)))
    private fun valid(x: Double) = x.isFinite()
    private fun angleOut(rad: Double, radians: Boolean) = if (radians) rad else Math.toDegrees(rad)
    private fun inputAngle(problem: GeometryProblem, id: String): Double? = problem.values[id]?.let { if (problem.radians) it else Math.toRadians(it) }
    private fun impossible(message: String) = GeometrySolveResult.Impossible(message)
    private fun solved(values: Map<String, Double>, formulas: List<String>, triangle: TriangleEngine.Result? = null): GeometrySolveResult {
        if(values.values.any { !it.isFinite() } || values.any { (key,value) -> key in setOf("area","perimeter","radius","diameter","circumference","arc","chord","sagitta","segmentArea","a","b","c") && value==0.0 })
            throw ArithmeticException("Résultats géométriques non représentables avec la précision numérique disponible.")
        return GeometrySolveResult.Solved(listOf(GeometrySolution(values, formulas, triangle)))
    }

    fun solve(problem: GeometryProblem): GeometrySolveResult = try {
        when (problem.figure) {
            GeometryFigure.TRIANGLE -> triangle(problem)
            GeometryFigure.CIRCLE -> circle(problem)
            GeometryFigure.PARALLELOGRAM -> parallelogram(problem)
        }
    } catch (_: ArithmeticException) {
        GeometrySolveResult.Indeterminate("Les valeurs dépassent la précision numérique disponible.")
    } catch (e: IllegalArgumentException) {
        val message=e.message ?: "Ces données ne décrivent pas une figure valide."
        if(message.contains("représentable",true)||message.contains("précision",true)||message.contains("numérique",true)) GeometrySolveResult.Indeterminate(message)
        else impossible(message)
    }

    private fun triangle(p: GeometryProblem): GeometrySolveResult {
        val v = p.values
        if(v.values.any { it<=0 }) return impossible("Les côtés, angles et mesures doivent être strictement positifs.")
        if(listOf("A","B","C").any { id -> inputAngle(p,id)?.let { it>=PI }==true }) return impossible("Chaque angle doit être inférieur à 180° (π rad).")
        val sides = listOf(v["a"], v["b"], v["c"])
        val a = sides[0]; val b = sides[1]; val c = sides[2]
        val angles = listOf(inputAngle(p, "A"), inputAngle(p, "B"), inputAngle(p, "C"))
        val candidates = mutableListOf<List<Double>>()
        if (p.variant == GeometryVariant.EQUILATERAL) {
            val side = v["a"] ?: v["b"] ?: v["c"] ?: v["perimeter"]?.div(3.0)
                ?: v["area"]?.let { sqrt(4.0 * it / sqrt(3.0)) }
                ?: listOf("hA","hB","hC").firstNotNullOfOrNull { v[it] }?.let { it * 2.0 / sqrt(3.0) }
                ?: return GeometrySolveResult.Insufficient
            require(side > 0 && valid(side)) { "La grandeur doit être strictement positive." }
            val r = TriangleEngine.calculate(side, side, side)
            return triangleResult(r, p, listOf("a = b = c", "A = B = C = 60°"))
        }
        if (p.variant == GeometryVariant.RIGHT) {
            val knownSides = sides.mapIndexedNotNull { i, x -> x?.let { i to it } }
            val rightAngle = angles[2]
            if (rightAngle != null && !close(rightAngle, PI / 2.0)) return impossible("Pour cette convention, l’angle droit est C.")
            val angleA = angles[0]; val angleB = angles[1]
            if (angleA?.let { it >= PI / 2.0 } == true || angleB?.let { it >= PI / 2.0 } == true)
                return impossible("Les angles A et B d’un triangle rectangle en C doivent être aigus.")
            if (angleA != null && angleB != null && !close(angleA + angleB, PI, 1e-7))
                return impossible("Les angles A et B doivent être complémentaires dans un triangle rectangle en C.")

            if (knownSides.size >= 2) {
                val (a, b, c) = sides
                val complete = when {
                    a != null && b != null -> {
                        val expectedC = hypot(a, b)
                        if (c != null && !close(c, expectedC)) return impossible("Les côtés sont incompatibles avec un triangle rectangle : a² + b² doit être égal à c².")
                        listOf(a, b, expectedC)
                    }
                    a != null && c != null -> {
                        if (c <= a) return impossible("Dans un triangle rectangle, l’hypoténuse c doit être supérieure au côté a.")
                        listOf(a, sqrt((c - a) * (c + a)), c)
                    }
                    b != null && c != null -> {
                        if (c <= b) return impossible("Dans un triangle rectangle, l’hypoténuse c doit être supérieure au côté b.")
                        listOf(sqrt((c - b) * (c + b)), b, c)
                    }
                    else -> return GeometrySolveResult.Insufficient
                }
                return triangleResult(TriangleEngine.calculate(complete[0], complete[1], complete[2]), p, listOf("a² + b² = c²", "A + B = 90°"))
            }
            val knownSideEntry = knownSides.singleOrNull()
            val angleIndex = angles.take(2).indexOfFirst { it != null }.takeIf { it >= 0 }
            val knownAngle = angleIndex?.let { angles[it] }
            if (knownSideEntry != null && knownAngle != null) {
                val (sideIndex, knownSide) = knownSideEntry
                require(knownSide > 0 && knownAngle > 0 && knownAngle < PI / 2) { "Le côté et l’angle aigu doivent être positifs." }
                val sinTheta = sin(knownAngle); val cosTheta = cos(knownAngle)
                val hypotenuse = when {
                    angleIndex == 0 && sideIndex == 0 -> knownSide / sinTheta
                    angleIndex == 0 && sideIndex == 1 -> knownSide / cosTheta
                    angleIndex == 1 && sideIndex == 1 -> knownSide / sinTheta
                    angleIndex == 1 && sideIndex == 0 -> knownSide / cosTheta
                    sideIndex == 2 -> knownSide
                    else -> return GeometrySolveResult.Insufficient
                }
                val vals = if (angleIndex == 0) listOf(hypotenuse*sinTheta,hypotenuse*cosTheta,hypotenuse)
                    else listOf(hypotenuse*cosTheta,hypotenuse*sinTheta,hypotenuse)
                val trigFormulas=if(angleIndex==0) listOf("sin(A) = a/c","cos(A) = b/c") else listOf("sin(B) = b/c","cos(B) = a/c")
                return triangleResult(TriangleEngine.calculate(vals[0], vals[1], vals[2]), p, trigFormulas+"a² + b² = c²")
            }
            return GeometrySolveResult.Insufficient
        }

        if (p.variant == GeometryVariant.ISOSCELES) {
            val base = c
            val equalLeg = a ?: b
            if (a != null && b != null && !close(a, b)) return impossible("Avec la convention choisie, les côtés égaux sont a et b.")
            if (base != null && equalLeg != null && (a == null || b == null)) {
                candidates += listOf(equalLeg, equalLeg, base)
            }
            if (base != null && v["hC"] != null) {
                val leg = hypot(base / 2.0, v["hC"]!!)
                candidates += listOf(leg, leg, base)
            }
            if (equalLeg != null && angles[2] != null) {
                val apex = angles[2]!!
                val side = 2.0 * equalLeg * sin(apex / 2.0)
                candidates += listOf(equalLeg, equalLeg, side)
            }
            if (equalLeg != null && angles[0] != null) {
                val baseAngle = angles[0]!!
                if (angles[1] != null && !close(angles[1]!!, baseAngle)) return impossible("Les angles à la base d’un triangle isocèle doivent être égaux.")
                val apex = PI - 2.0 * baseAngle
                if (apex > EPS) candidates += listOf(equalLeg, equalLeg, equalLeg * sin(apex) / sin(baseAngle))
            }
        }

        // Supplemental measurements participate as consistency checks after a classic solve.
        if (a != null && b != null && c != null) candidates += listOf(a,b,c)
        if (v["perimeter"] != null && sides.count { it != null } == 2) {
            val derived=v["perimeter"]!!-sides.filterNotNull().sum()
            if(derived<=0) return impossible("Le périmètre est inférieur ou égal à la somme des côtés connus.")
            candidates += sides.map { it ?: derived }
        }
        if(v["area"]!=null && sides.count { it!=null }==2) {
            val pair=sides.indices.filter { sides[it]!=null };val i=pair[0];val j=pair[1];val k=3-i-j
            val ratio=2*v["area"]!!/(sides[i]!!*sides[j]!!)
            if(ratio<=0||ratio>1+EPS) return impossible("L’aire est incompatible avec les côtés saisis.")
            val included=asin(ratio.coerceIn(0.0,1.0))
            listOf(included,PI-included).distinctBy { (it*1e10).roundToLong() }.forEach { angle ->
                val opposite=sqrt(sides[i]!!*sides[i]!!+sides[j]!!*sides[j]!!-2*sides[i]!!*sides[j]!!*cos(angle))
                val all=MutableList(3){0.0};all[i]=sides[i]!!;all[j]=sides[j]!!;all[k]=opposite;candidates+=all
            }
        }
        // SAS: angle A is between b,c; B between a,c; C between a,b.
        listOf(Triple(0,1,2), Triple(1,0,2), Triple(2,0,1)).forEach { (ai, s1, s2) ->
            val angle = angles[ai]; val x = sides[s1]; val y = sides[s2]
            if (angle != null && x != null && y != null) {
                val opposite = sqrt(x*x + y*y - 2*x*y*cos(angle))
                val all = MutableList(3) { 0.0 }; all[s1]=x; all[s2]=y; all[ai]=opposite
                candidates += all
            }
        }
        // ASA/AAS: two angles and any side.
        val knownAngleIndices = angles.indices.filter { angles[it] != null }
        if (knownAngleIndices.size >= 2) {
            val fullAngles = angles.toMutableList()
            if(knownAngleIndices.size==2) {
                val third = PI - knownAngleIndices.sumOf { angles[it]!! }
                require(third > EPS) { "La somme des angles doit être inférieure à 180° (π rad)." }
                fullAngles[(0..2).first { fullAngles[it] == null }]=third
            } else if(!close(knownAngleIndices.sumOf { angles[it]!! },PI)) {
                return impossible("La somme des angles doit être égale à 180° (π rad).")
            }
            val knownSideIndex = sides.indices.firstOrNull { sides[it] != null }
            if (knownSideIndex != null) {
                val ratio = sides[knownSideIndex]!! / sin(fullAngles[knownSideIndex]!!)
                candidates += (0..2).map { ratio * sin(fullAngles[it]!!) }
            } else {
                val sine=fullAngles.map { sin(it!!) }
                val scale=when {
                    v["perimeter"]!=null -> v["perimeter"]!!/sine.sum()
                    v["area"]!=null -> sqrt(2*v["area"]!!/(sine[0]*sine[1]*sine[2]))
                    v["hA"]!=null -> v["hA"]!!/(sine[1]*sine[2])
                    v["hB"]!=null -> v["hB"]!!/(sine[0]*sine[2])
                    v["hC"]!=null -> v["hC"]!!/(sine[0]*sine[1])
                    else -> Double.NaN
                }
                if(scale.isFinite()&&scale>0) candidates += sine.map { scale*it }
            }
        }
        // SSA: retain both supplementary-angle solutions when valid.
        for (angleIndex in 0..2) for (oppositeIndex in 0..2) if (angleIndex != oppositeIndex) {
            val theta = angles[angleIndex] ?: continue
            val opposite = sides[angleIndex] ?: continue
            val other = sides[oppositeIndex] ?: continue
            if (theta <= 0 || theta >= PI || opposite <= 0 || other <= 0) continue
            val ratio = other * sin(theta) / opposite
            if (ratio > 1.0 + EPS) continue
            val candidateAngles = if (abs(1.0-ratio) <= 1e-12) listOf(PI/2.0) else {
                val candidateAngle = asin(ratio.coerceIn(-1.0,1.0))
                listOf(candidateAngle, PI-candidateAngle)
            }
            candidateAngles.distinctBy { (it*1e10).roundToLong() }.forEach { second ->
                val missingIndex = 3-angleIndex-oppositeIndex
                val third = PI-theta-second
                if (third <= EPS) return@forEach
                val full = MutableList(3) {0.0}; full[angleIndex]=theta; full[oppositeIndex]=second; full[missingIndex]=third
                val k = opposite/sin(theta)
                candidates += full.map { k*sin(it) }
            }
        }
        if (candidates.isEmpty()) {
            val knownCount = sides.count { it != null } + angles.count { it != null } + listOf("area","perimeter","hA","hB","hC").count { v[it] != null }
            if(sides.all { it==null } && angles.count { it!=null }>=2) return GeometrySolveResult.Insufficient
            if (sides.count { it != null } + angles.count { it != null } >= 3) return impossible("Les côtés et angles saisis ne peuvent appartenir au même triangle.")
            return if (knownCount < 3) GeometrySolveResult.Insufficient else GeometrySolveResult.Unsupported("Cette combinaison de données triangulaires n’est pas prise en charge.")
        }
        val results = candidates.mapNotNull { s -> runCatching { TriangleEngine.calculate(s[0],s[1],s[2]) }.getOrNull() }
            .filter { p.variant != GeometryVariant.ISOSCELES || close(it.a, it.b) }
            .filter { r ->
                val measured = listOf(r.a,r.b,r.c); val computedAngles = listOf(r.angleA,r.angleB,r.angleC)
                sides.indices.all { sides[it] == null || close(sides[it]!!,measured[it]) } &&
                    angles.indices.all { angles[it] == null || close(angles[it]!!,Math.toRadians(computedAngles[it]),1e-7) } &&
                    (v["area"] == null || close(v["area"]!!,r.area)) &&
                    (v["perimeter"] == null || close(v["perimeter"]!!,r.perimeter)) &&
                    listOf(r.heightA,r.heightB,r.heightC).indices.all { i -> v[listOf("hA","hB","hC")[i]]?.let { close(it,listOf(r.heightA,r.heightB,r.heightC)[i]) } ?: true }
            }.fold(mutableListOf<TriangleEngine.Result>()) { unique, result ->
                if (unique.none { close(it.a,result.a,1e-9) && close(it.b,result.b,1e-9) && close(it.c,result.c,1e-9) }) unique += result
                unique
            }
        if (results.isEmpty()) return impossible("Les contraintes saisies sont contradictoires ou ne forment pas un triangle.")
        val usedFormulas = when {
            p.variant == GeometryVariant.ISOSCELES -> listOf("a = b", "Loi des cosinus", "Formule de Héron")
            v["area"]!=null && sides.count { it!=null }==2 -> {
                val pair=sides.indices.filter { sides[it]!=null };val opposite=3-pair[0]-pair[1]
                val relation=when(opposite) { 0->"S = bc sin(A)/2";1->"S = ca sin(B)/2";else->"S = ab sin(C)/2" }
                listOf(relation,"Loi des cosinus")
            }
            sides.all { it != null } -> listOf("Loi des cosinus", "Formule de Héron")
            angles.count { it != null } >= 2 -> listOf("Somme des angles", "Loi des sinus")
            else -> listOf("Loi des sinus")
        }
        val solutions = results.map { r -> triangleSolution(r, p, usedFormulas) }
        return GeometrySolveResult.Solved(solutions)
    }

    private fun triangleResult(r: TriangleEngine.Result, p: GeometryProblem, formulas: List<String>): GeometrySolveResult {
        val measurements = p.values
        val checks = listOf("a" to r.a,"b" to r.b,"c" to r.c,"area" to r.area,"perimeter" to r.perimeter,"hA" to r.heightA,"hB" to r.heightB,"hC" to r.heightC)
        if (checks.any { (k,x) -> measurements[k]?.let { !close(it,x) } == true } ||
            listOf("A" to r.angleA,"B" to r.angleB,"C" to r.angleC).any { (k,x) -> inputAngle(p,k)?.let { !close(it,Math.toRadians(x),1e-7) } == true })
            return impossible("Les contraintes saisies sont contradictoires.")
        return GeometrySolveResult.Solved(listOf(triangleSolution(r,p,formulas)))
    }

    private fun triangleSolution(r: TriangleEngine.Result, p: GeometryProblem, formulas: List<String>): GeometrySolution {
        val values = linkedMapOf("a" to r.a,"b" to r.b,"c" to r.c,"A" to angleOut(Math.toRadians(r.angleA),p.radians),"B" to angleOut(Math.toRadians(r.angleB),p.radians),"C" to angleOut(Math.toRadians(r.angleC),p.radians),"perimeter" to r.perimeter,"area" to r.area,"hA" to r.heightA,"hB" to r.heightB,"hC" to r.heightC)
        val s = (r.a+r.b+r.c)/2.0
        values["inradius"] = r.area/s
        values["circumradius"] = r.a*r.b*r.c/(4.0*r.area)
        if(values.values.any { !it.isFinite() }) throw ArithmeticException("Rayons du triangle non représentables avec la précision disponible.")
        val used=(formulas+listOf("Loi des cosinus","Formule de Héron","P = a + b + c","hₐ = 2S/a","h_b = 2S/b","h꜀ = 2S/c","r = S/s","R = abc/(4S)")).distinct()
        return GeometrySolution(values,used,r)
    }

    private fun circle(p: GeometryProblem): GeometrySolveResult {
        val v=p.values
        val pi=PI
        return when(p.variant) {
            GeometryVariant.FULL_CIRCLE -> {
                val candidates=listOf(v["radius"],v["diameter"]?.div(2),v["circumference"]?.div(2*pi),v["area"]?.let { sqrt(it/pi) }).filterNotNull()
                if(candidates.isEmpty()) return GeometrySolveResult.Insufficient
                val r=candidates.first(); require(r>0&&valid(r)) { "Le rayon doit être strictement positif." }
                if(candidates.any { !close(it,r) }) return impossible("Les mesures du cercle sont contradictoires.")
                solved(mapOf("radius" to r,"diameter" to 2*r,"circumference" to 2*pi*r,"area" to pi*r*r),listOf("d = 2r","P = 2πr","S = πr²"))
            }
            GeometryVariant.ARC_SECTOR -> {
                var r=v["radius"]
                var theta=inputAngle(p,"angle")
                if (r == null && theta != null && abs(theta) > EPS) r = v["arc"]?.div(abs(theta)) ?: v["sectorArea"]?.let { sqrt(2*it/abs(theta!!)) }
                if (r == null && v["arc"] != null && v["sectorArea"] != null) {
                    r=2*v["sectorArea"]!!/v["arc"]!!; theta=v["arc"]!!/r
                }
                r ?: return GeometrySolveResult.Insufficient
                theta = theta ?: v["arc"]?.let { it/r!! } ?: v["sectorArea"]?.let { 2*it/(r!!*r!!) } ?: return GeometrySolveResult.Insufficient
                require(r!!>0&&abs(theta!!)>EPS&&abs(theta!!)<=2*pi+EPS) { "Le rayon doit être positif et l’angle non nul, limité à un tour complet (±360° ou ±2π rad)." }
                val magnitude = abs(theta!!)
                val arc=r!!*magnitude; val sector=r!!*r!!*magnitude/2
                if(v["arc"]?.let { !close(it,arc) }==true || v["sectorArea"]?.let { !close(it,sector) }==true) return impossible("Les mesures de l’arc et du secteur sont contradictoires.")
                solved(mapOf("radius" to r!!,"circumference" to 2*pi*r!!,"area" to pi*r!!*r!!,"angle" to angleOut(theta!!,p.radians),"arc" to arc,"sectorArea" to sector),listOf("P = 2πr","S = πr²","s = r|θ| (longueur positive), θ signé dans le sens de parcours","S secteur = r²|θ|/2 (θ en radians, aire positive)"))
            }
            GeometryVariant.MINOR_SEGMENT, GeometryVariant.MAJOR_SEGMENT -> segment(p)
            else -> GeometrySolveResult.Unsupported("Sous-type de cercle non pris en charge.")
        }
    }

    private fun segment(p: GeometryProblem): GeometrySolveResult {
        val v=p.values
        val isMajor=p.variant==GeometryVariant.MAJOR_SEGMENT
        var theta=inputAngle(p,"angle")
        var r=v["radius"]
        if(r==null) {
            val chord=v["chord"];val distance=v["distance"];val sagitta=v["sagitta"]
            r=when {
                chord!=null&&distance!=null -> hypot(chord/2,distance)
                chord!=null&&distance==0.0 -> chord/2
                chord!=null&&sagitta!=null&&sagitta>0 -> chord*chord/(8*sagitta)+sagitta/2
                distance!=null&&sagitta!=null -> if(isMajor) sagitta-distance else sagitta+distance
                else -> null
            }
            if(r!=null&&r!!>0) {
                val minor=when {
                    chord!=null -> 2*asin((chord/(2*r!!)).coerceIn(0.0,1.0))
                    distance!=null -> 2*acos((distance/r!!).coerceIn(0.0,1.0))
                    else -> 2*acos((1-sagitta!!/r!!).coerceIn(-1.0,1.0)).let { if(isMajor) 2*PI-it else it }
                }
                theta=if(isMajor) 2*PI-minor else minor
                if(sagitta!=null && isMajor!=(sagitta>r!!)) return impossible("La flèche saisie ne correspond pas au segment sélectionné.")
            }
        }
        if(r==null&&theta!=null) {
            val magnitude=abs(theta!!); val minor=min(magnitude,2*PI-magnitude)
            r=v["chord"]?.let { it/(2*sin(minor/2)) }
                ?: v["distance"]?.let { if(abs(cos(minor/2))<EPS) null else it/cos(minor/2) }
                ?: v["sagitta"]?.let { val denominator=1-cos(magnitude/2);if(abs(denominator)<EPS)null else it/denominator }
                ?: v["segmentArea"]?.let {
                    val base=minor-sin(minor)
                    val divisor=if(isMajor) 2*PI-base else base
                    if(divisor<=EPS) null else sqrt(2*it/divisor)
                }
        }
        r ?: return if(v.size<2) GeometrySolveResult.Insufficient else GeometrySolveResult.Unsupported("Cette combinaison de corde, flèche, distance et aire de segment n’est pas prise en charge.")
        if(!r!!.isFinite()||r!!<=0.0) return impossible("Le rayon doit être strictement positif et fini.")
        require(r!!>0&&valid(r!!)) { "Le rayon doit être strictement positif." }
        if(theta==null) v["chord"]?.let { c -> require(c>0&&c<=2*r!!*(1+EPS)) { "La corde doit être comprise entre 0 et le diamètre." }; theta=2*asin((c/(2*r!!)).coerceIn(0.0,1.0)); if(isMajor) theta=2*PI-theta }
        if(theta==null) v["distance"]?.let { d -> require(d>=0&&d<=r!!) { "La distance au centre doit être comprise entre 0 et le rayon." }; theta=2*acos((d/r!!).coerceIn(-1.0,1.0)); if(isMajor) theta=2*PI-theta }
        if(theta==null) v["sagitta"]?.let { h -> require(h>0&&h<=2*r!!) { "La flèche doit être comprise entre 0 et le diamètre." }; val minor=2*acos((1-h/r!!).coerceIn(-1.0,1.0)); theta=if(isMajor) 2*PI-minor else minor }
        if(theta==null) v["segmentArea"]?.let { area ->
            val total=PI*r*r
            require(area>0&&area<total) { "L’aire du segment doit être comprise entre 0 et l’aire du cercle." }
            val target=if(isMajor) total-area else area
            var low=0.0;var high=PI
            repeat(100) { val mid=(low+high)/2; if(r*r*(mid-sin(mid))/2<target) low=mid else high=mid }
            theta=if(isMajor) 2*PI-(low+high)/2 else (low+high)/2
        }
        theta ?: return if(v.size<2) GeometrySolveResult.Insufficient else GeometrySolveResult.Unsupported("Cette combinaison de données de segment n’est pas prise en charge.")
        require(abs(theta!!)>EPS&&abs(theta!!)<=2*PI+EPS) { "La valeur absolue de l’angle doit être comprise entre 0 et un tour complet (360° ou 2π rad)." }
        val magnitude=abs(theta!!).coerceAtMost(2*PI); val minorAngle=min(magnitude,2*PI-magnitude)
        if(isMajor&&magnitude<=PI+EPS) return impossible("Un segment majeur exige une valeur absolue d’angle supérieure à 180°.")
        if(!isMajor&&magnitude>PI+EPS) return impossible("Un segment mineur exige une valeur absolue d’angle inférieure ou égale à 180°.")
        val chord=2*r!!*sin(minorAngle/2); val distance=r!!*cos(minorAngle/2); val sagitta=r!!*(1-cos(magnitude/2))
        val minorArea=r!!*r!!*(minorAngle-sin(minorAngle))/2
        val area=if(isMajor) PI*r!!*r!!-minorArea else minorArea
        val expected=mapOf("chord" to chord,"distance" to distance,"sagitta" to sagitta,"segmentArea" to area)
        if(expected.any { (k,x)->v[k]?.let { !close(it,x) }==true }) return impossible("Les mesures de la corde ou du segment sont contradictoires.")
        val formulas=listOf("P = 2πr","S = πr²","c = 2r sin(θ/2)","d = r|cos(θ/2)|","h = r(1 − cos(θ/2))") + if(isMajor) listOf("S segment = S cercle − S mineur") else listOf("S segment = r²(θ − sin θ)/2")
        return solved(mapOf("radius" to r!!,"circumference" to 2*PI*r!!,"area" to PI*r!!*r!!,"angle" to angleOut(theta!!,p.radians),"chord" to chord,"distance" to distance,"sagitta" to sagitta,"segmentArea" to area),formulas)
    }

    private fun parallelogram(p: GeometryProblem): GeometrySolveResult {
        val v=p.values; val isRect=p.variant==GeometryVariant.RECTANGLE; val isRhombus=p.variant==GeometryVariant.RHOMBUS; val isSquare=p.variant==GeometryVariant.SQUARE
        var a=v["a"]; var b=v["b"]; var theta=inputAngle(p,"angle")
        if(isSquare) {
            val side=a ?: b ?: v["diagonal1"]?.div(sqrt(2.0)) ?: v["diagonal2"]?.div(sqrt(2.0)) ?: v["area"]?.let { sqrt(it) } ?: v["perimeter"]?.div(4) ?: return GeometrySolveResult.Insufficient
            require(side>0&&valid(side)) { "La grandeur doit être strictement positive." }
            if(listOf(a,b).filterNotNull().any { !close(it,side) } || v["diagonal1"]?.let { !close(it,side*sqrt(2.0)) }==true || v["diagonal2"]?.let { !close(it,side*sqrt(2.0)) }==true || v["area"]?.let { !close(it,side*side) }==true || v["perimeter"]?.let { !close(it,4*side) }==true) return impossible("Les contraintes sont incompatibles avec un carré.")
            return parallelogramSolution(side,side,PI/2,p,listOf("d = a√2","S = a²","P = 4a"),true)
        }
        if(isRect && (a==null || b==null)) {
            if(a!=null && v["area"]!=null) b=v["area"]!!/a
            else if(b!=null && v["area"]!=null) a=v["area"]!!/b
            else if(a!=null && v["perimeter"]!=null) b=v["perimeter"]!!/2-a
            else if(b!=null && v["perimeter"]!=null) a=v["perimeter"]!!/2-b
            else if(v["area"]!=null && v["perimeter"]!=null) {
                val sum=v["perimeter"]!!/2; val disc=sum*sum-4*v["area"]!!
                if(disc < -EPS) return impossible("Aire et périmètre incompatibles avec un rectangle.")
                val root=sqrt(max(0.0,disc)); val first=(sum+root)/2; val second=(sum-root)/2
                if(first<=0||second<=0) return impossible("Aire et périmètre incompatibles avec un rectangle.")
                return parallelogramSolution(first,second,PI/2,p,listOf("P = 2(a+b)","S = ab","d² = a²+b²"),true)
            }
        }
        if (isRect) theta = theta ?: PI / 2.0
        if (isRhombus) {
            if (a == null && b == null && v["perimeter"] != null) {
                val side = v["perimeter"]!! / 4.0
                require(side > 0 && valid(side)) { "Le périmètre doit être strictement positif et fini." }
                a = side; b = side
            }
            if (a == null && b == null && v["area"] != null && theta != null) {
                require(v["area"]!! > 0 && valid(v["area"]!!) && theta!! > 0 && theta!! < PI) { "L’aire doit être positive et l’angle intérieur compris entre 0 et 180° (ou 0 et π rad)." }
                val sine = sin(theta!!)
                if (sine <= EPS) return impossible("L’aire est incompatible avec cet angle de losange.")
                a = sqrt(v["area"]!! / sine); b = a
            }
        }
        if(isRhombus && a==null && b!=null) a=b
        if(isRhombus && b==null && a!=null) b=a
        val d1=v["diagonal1"]; val d2=v["diagonal2"]
        if(a!=null&&b!=null&&theta==null) {
            val area=v["area"] ?: v["heightA"]?.times(a) ?: v["heightB"]?.times(b)
            if(area!=null) {
                val ratio=area/(a*b)
                if(ratio<=0||ratio>1+EPS) return impossible("L’aire est incompatible avec les côtés.")
                val acute=asin(ratio.coerceIn(0.0,1.0))
                // Supplementary included angles are mirror-congruent when no orientation is given.
                val angles=listOf(acute)
                val solutions=angles.mapNotNull { (parallelogramSolution(a,b,it,p,listOf("S = ab sin(θ)","d₁² = a²+b²+2ab cos(θ)","d₂² = a²+b²−2ab cos(θ)"),false) as? GeometrySolveResult.Solved)?.solutions?.singleOrNull() }
                if(solutions.isNotEmpty()) return GeometrySolveResult.Solved(solutions)
                return impossible("Les contraintes saisies sont contradictoires.")
            }
        }
        if(!isRect&&!isRhombus&&theta==null&&((a==null&&b!=null)||(a!=null&&b==null))&&v["perimeter"]!=null&&v["area"]!=null) {
            val known=a?:b!!;val other=v["perimeter"]!!/2-known
            if(other<=0) return impossible("Le périmètre doit laisser une longueur positive pour l’autre côté.")
            if(a==null) a=other else b=other
            val ratio=v["area"]!!/(a!!*b!!)
            if(ratio<=0||ratio>1+EPS) return impossible("Aire et périmètre incompatibles avec les côtés saisis.")
            theta=asin(ratio.coerceIn(0.0,1.0))
        }
        if(a!=null&&b!=null&&theta==null) {
            val d=d1?:d2
            if(d!=null) {
                val cosTheta=if(d1!=null) (d*d-a*a-b*b)/(2*a*b) else (a*a+b*b-d*d)/(2*a*b)
                if(cosTheta !in -1.0..1.0) return impossible("Cette diagonale est incompatible avec les côtés.")
                theta=acos(cosTheta)
            }
        }
        if((isRect||isRhombus) && theta==null) theta=if(isRect) PI/2 else null
        if(a==null || b==null || theta==null) {
            if(isRect) {
                val side=a?:b; val diag=d1?:d2
                if(side!=null&&diag!=null) { require(side>0&&diag>side) { "La diagonale doit être supérieure au côté." }; val other=sqrt(diag*diag-side*side); if(a!=null) b=other else a=other;theta=PI/2 }
            }
            if(isRhombus) {
                if(d1!=null&&d2!=null) { require(d1>0&&d2>0); a=hypot(d1/2,d2/2); b=a; theta=2*atan2(d2,d1) }
            }
        }
        if(a==null||b==null||theta==null) {
            if (isRhombus && a != null && b != null && theta == null && v["perimeter"] != null) {
                if (!close(v["perimeter"]!!, 4*a!!)) return impossible("Le périmètre et le côté saisis sont contradictoires.")
                if (v["area"]?.let { it <= 0 || it > a!!*a!!*(1+EPS) } == true) return impossible("L’aire est incompatible avec le côté du losange.")
                return GeometrySolveResult.Solved(listOf(GeometrySolution(
                    values = mapOf("a" to a!!, "b" to b!!, "perimeter" to 4*a!!),
                    formulas = listOf("a = P/4", "P = 4a"),
                    note = "Côtés déterminés ; renseignez θ ou les diagonales pour calculer la forme complète."
                )))
            }
            return if(v.size<3) GeometrySolveResult.Insufficient else GeometrySolveResult.Unsupported("Cette combinaison de données de parallélogramme n’est pas prise en charge.")
        }
        require(a>0&&b>0&&theta>0&&theta<PI) { "Les côtés et l’angle doivent définir une figure non dégénérée." }
        if(isRect&&!close(theta,PI/2)) return impossible("Un rectangle doit avoir des angles droits.")
        if(isRhombus&&!close(a,b)) return impossible("Un losange doit avoir quatre côtés égaux.")
        val formulas=when {
            isRect -> listOf("S = ab", "P = 2(a+b)", "d² = a²+b²")
            isRhombus && v["perimeter"] != null && v["a"] == null && v["b"] == null -> listOf("a = P/4", "S = ab sin(θ)", "P = 2(a+b)", "d₁² = a² + b² + 2ab cos(θ)", "d₂² = a² + b² − 2ab cos(θ)")
            isRhombus -> listOf("S = ab sin(θ)","P = 2(a+b)","d₁² = a² + b² + 2ab cos(θ)","d₂² = a² + b² − 2ab cos(θ)")
            else -> listOf("S = ab sin(θ)","P = 2(a+b)","d₁² = a² + b² + 2ab cos(θ)","d₂² = a² + b² − 2ab cos(θ)")
        }
        return parallelogramSolution(a,b,theta,p,formulas,isRect)
    }

    private fun parallelogramSolution(a:Double,b:Double,theta:Double,p:GeometryProblem,formulas:List<String>,rectangle:Boolean):GeometrySolveResult {
        val d1=sqrt(a*a+b*b+2*a*b*cos(theta)); val d2=sqrt(a*a+b*b-2*a*b*cos(theta)); val area=a*b*sin(theta); val perimeter=2*(a+b)
        val heightA=area/a;val heightB=area/b
        val values=mapOf("a" to a,"b" to b,"angle" to angleOut(theta,p.radians),"oppositeAngle" to angleOut(PI-theta,p.radians),"diagonal1" to d1,"diagonal2" to d2,"heightA" to heightA,"heightB" to heightB,"perimeter" to perimeter,"area" to area)
        val constraints=listOf("a" to a,"b" to b,"diagonal1" to d1,"diagonal2" to d2,"heightA" to heightA,"heightB" to heightB,"area" to area,"perimeter" to perimeter)
        if(constraints.any{(k,x)->p.values[k]?.let{!close(it,x)}==true}) return impossible("Les contraintes saisies sont contradictoires.")
        if(p.values["angle"]?.let{!close(inputAngle(p,"angle")!!,theta)}==true) return impossible("L’angle saisi est incompatible avec la figure.")
        return solved(values,(formulas+listOf("hₐ = S/a","h_b = S/b")).distinct())
    }
}
