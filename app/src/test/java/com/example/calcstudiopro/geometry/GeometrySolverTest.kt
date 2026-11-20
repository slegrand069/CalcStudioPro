package com.example.calcstudiopro.geometry

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class GeometrySolverTest {
    private fun tri(values: Map<String,Double>, type: GeometryVariant=GeometryVariant.GENERAL, rad:Boolean=false) =
        GeometrySolver.solve(GeometryProblem(GeometryFigure.TRIANGLE,type,values,rad))
    private fun circle(type:GeometryVariant,values:Map<String,Double>,rad:Boolean=false)=
        GeometrySolver.solve(GeometryProblem(GeometryFigure.CIRCLE,type,values,rad))
    private fun para(type:GeometryVariant,values:Map<String,Double>,rad:Boolean=false)=
        GeometrySolver.solve(GeometryProblem(GeometryFigure.PARALLELOGRAM,type,values,rad))
    private fun one(result:GeometrySolveResult)= (result as GeometrySolveResult.Solved).solutions.single()

    @Test fun `triangle SSS SAS ASA AAS and SSA cardinality`() {
        assertEquals(6.0,one(tri(mapOf("a" to 3.0,"b" to 4.0,"c" to 5.0))).values["area"]!!,1e-12)
        val sas=one(tri(mapOf("a" to 3.0,"b" to 4.0,"C" to 90.0)))
        assertEquals(5.0,sas.values["c"]!!,1e-12)
        val asa=one(tri(mapOf("A" to 60.0,"B" to 60.0,"c" to 2.0)))
        assertEquals(2.0,asa.values["a"]!!,1e-12)
        val aas=one(tri(mapOf("A" to 30.0,"B" to 60.0,"a" to 4.0)))
        assertEquals(90.0,aas.values["C"]!!,1e-10)
        assertEquals(5.0,one(tri(mapOf("a" to 3.0,"b" to 4.0,"perimeter" to 12.0))).values["c"]!!,1e-12)
        assertEquals(2,(tri(mapOf("a" to 5.0,"b" to 7.0,"area" to 14.0)) as GeometrySolveResult.Solved).solutions.size)
        assertEquals(6.0,one(tri(mapOf("A" to 60.0,"B" to 60.0,"C" to 60.0,"area" to 9*sqrt(3.0)))).values["c"]!!,1e-12)
        assertTrue(tri(mapOf("A" to 60.0,"B" to 60.0,"C" to 60.0)) is GeometrySolveResult.Insufficient)
        assertEquals(2,(tri(mapOf("A" to 30.0,"a" to 10.0,"b" to 11.0)) as GeometrySolveResult.Solved).solutions.size)
        assertEquals(1,(tri(mapOf("A" to 30.0,"a" to 10.0,"b" to 20.0)) as GeometrySolveResult.Solved).solutions.size)
        assertTrue(tri(mapOf("A" to 30.0,"a" to 10.0,"b" to 25.0)) is GeometrySolveResult.Impossible)
    }

    @Test fun `triangle constrained types supplementary data and angle units`() {
        val right=one(tri(mapOf("a" to 3.0,"b" to 4.0),GeometryVariant.RIGHT))
        assertEquals(5.0,right.values["c"]!!,1e-12)
        val isosceles=one(tri(mapOf("a" to 5.0,"c" to 6.0),GeometryVariant.ISOSCELES))
        assertEquals(5.0,isosceles.values["b"]!!,1e-12)
        assertTrue(tri(mapOf("a" to 5.0,"b" to 6.0,"c" to 7.0),GeometryVariant.ISOSCELES) is GeometrySolveResult.Impossible)
        val eq=one(tri(mapOf("area" to 9*sqrt(3.0)),GeometryVariant.EQUILATERAL))
        assertEquals(6.0,eq.values["a"]!!,1e-12)
        assertTrue(tri(mapOf("a" to 3.0,"b" to 4.0,"c" to 5.0,"area" to 7.0)) is GeometrySolveResult.Impossible)
        val rad=one(tri(mapOf("A" to PI/3,"B" to PI/3,"a" to 2.0),rad=true))
        assertEquals(PI/3,rad.values["C"]!!,1e-12)
        assertTrue(tri(emptyMap()) is GeometrySolveResult.Insufficient)
    }

    @Test fun `right triangle always enforces the right angle and hypotenuse`() {
        val threeFourFive = one(tri(mapOf("a" to 3.0, "b" to 4.0, "c" to 5.0), GeometryVariant.RIGHT))
        assertEquals(90.0, threeFourFive.values["C"]!!, 1e-12)
        val inferred = one(tri(mapOf("a" to 5.0, "b" to 6.0), GeometryVariant.RIGHT))
        assertEquals(kotlin.math.sqrt(61.0), inferred.values["c"]!!, 1e-12)
        assertTrue(tri(mapOf("a" to 5.0, "b" to 6.0, "c" to 7.0), GeometryVariant.RIGHT) is GeometrySolveResult.Impossible)
        assertTrue(tri(mapOf("a" to 5.0, "b" to 6.0, "c" to 7.0, "C" to 90.0), GeometryVariant.RIGHT) is GeometrySolveResult.Impossible)
        assertTrue(tri(mapOf("a" to 3.0, "b" to 4.0, "c" to 5.0, "A" to 50.0), GeometryVariant.RIGHT) is GeometrySolveResult.Impossible)
        assertTrue(tri(mapOf("a" to 3.0, "A" to 40.0, "B" to 45.0), GeometryVariant.RIGHT) is GeometrySolveResult.Impossible)
        val tolerance = one(tri(mapOf("a" to 3.0, "b" to 4.0, "c" to (5.0 + 1e-9)), GeometryVariant.RIGHT))
        assertEquals(90.0, tolerance.values["C"]!!, 1e-6)
    }

    @Test fun `isosceles base participates as an editable independent side`() {
        val result = one(tri(mapOf("a" to 5.0, "c" to 7.0), GeometryVariant.ISOSCELES))
        assertEquals(5.0, result.values["b"]!!, 1e-12)
        assertEquals(7.0, result.values["c"]!!, 1e-12)
        val changedBase = one(tri(mapOf("a" to 5.0, "c" to 8.5), GeometryVariant.ISOSCELES))
        assertEquals(8.5, changedBase.values["c"]!!, 1e-12)
        assertTrue(tri(mapOf("a" to 5.0, "c" to 10.0), GeometryVariant.ISOSCELES) is GeometrySolveResult.Impossible)
    }

    @Test fun `circle complete arc sector and segment forward inverse`() {
        val full=one(circle(GeometryVariant.FULL_CIRCLE,mapOf("diameter" to 10.0)))
        assertEquals(25*PI,full.values["area"]!!,1e-12)
        assertEquals(10*PI,full.values["circumference"]!!,1e-12)
        val arc=one(circle(GeometryVariant.ARC_SECTOR,mapOf("radius" to 4.0,"angle" to 90.0)))
        assertEquals(2*PI,arc.values["arc"]!!,1e-12)
        assertEquals(8*PI,arc.values["circumference"]!!,1e-12)
        assertEquals(16*PI,arc.values["area"]!!,1e-12)
        val inverse=one(circle(GeometryVariant.ARC_SECTOR,mapOf("angle" to 90.0,"arc" to 2*PI)))
        assertEquals(4.0,inverse.values["radius"]!!,1e-12)
        val minor=one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"angle" to 90.0)))
        assertEquals(2*sqrt(2.0),minor.values["chord"]!!,1e-12)
        val major=one(circle(GeometryVariant.MAJOR_SEGMENT,mapOf("radius" to 2.0,"angle" to 270.0)))
        assertTrue(major.values["segmentArea"]!!>2*PI)
        val negativeMinor=one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"angle" to -90.0)))
        assertEquals(-90.0,negativeMinor.values["angle"]!!,1e-12)
        assertEquals(minor.values["segmentArea"]!!,negativeMinor.values["segmentArea"]!!,1e-12)
        val negativeMajor=one(circle(GeometryVariant.MAJOR_SEGMENT,mapOf("radius" to 2.0,"angle" to -270.0)))
        assertEquals(-270.0,negativeMajor.values["angle"]!!,1e-12)
        assertEquals(major.values["segmentArea"]!!,negativeMajor.values["segmentArea"]!!,1e-12)
        assertTrue(circle(GeometryVariant.MAJOR_SEGMENT,mapOf("radius" to 2.0,"angle" to -90.0)) is GeometrySolveResult.Impossible)
        val fromArea=one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"segmentArea" to minor.values["segmentArea"]!!)))
        assertEquals(90.0,fromArea.values["angle"]!!,1e-7)
        assertEquals(90.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"chord" to 2*sqrt(2.0)))).values["angle"]!!,1e-10)
        assertEquals(90.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"distance" to sqrt(2.0)))).values["angle"]!!,1e-10)
        assertEquals(90.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0,"sagitta" to 2-sqrt(2.0)))).values["angle"]!!,1e-10)
        val inferredRadius=one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("angle" to 90.0,"chord" to 2*sqrt(2.0))))
        assertEquals(2.0,inferredRadius.values["radius"]!!,1e-12)
        assertEquals(2.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("angle" to 90.0,"segmentArea" to minor.values["segmentArea"]!!))).values["radius"]!!,1e-10)
        assertEquals(2.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("chord" to 2*sqrt(2.0),"distance" to sqrt(2.0)))).values["radius"]!!,1e-12)
        assertEquals(2.0,one(circle(GeometryVariant.MINOR_SEGMENT,mapOf("chord" to 2*sqrt(2.0),"sagitta" to 2-sqrt(2.0)))).values["radius"]!!,1e-12)
        assertTrue(one(circle(GeometryVariant.FULL_CIRCLE,mapOf("radius" to 2.0))).formulaIds.all { it.startsWith("circle.") })
        assertTrue(circle(GeometryVariant.FULL_CIRCLE,mapOf("radius" to 2.0,"diameter" to 5.0)) is GeometrySolveResult.Impossible)
        assertTrue(circle(GeometryVariant.MAJOR_SEGMENT,mapOf("radius" to 2.0,"angle" to 90.0)) is GeometrySolveResult.Impossible)
        assertTrue(circle(GeometryVariant.MINOR_SEGMENT,mapOf("radius" to 2.0)) is GeometrySolveResult.Insufficient)
        assertTrue(circle(GeometryVariant.MINOR_SEGMENT,mapOf("chord" to 1.0,"segmentArea" to 0.1)) is GeometrySolveResult.Unsupported)
    }

    @Test fun `arc sector accepts signed angles and keeps measures positive`() {
        val clockwise = one(circle(GeometryVariant.ARC_SECTOR, mapOf("radius" to 2.0, "angle" to -90.0)))
        assertEquals(-90.0, clockwise.values["angle"]!!, 1e-12)
        assertEquals(PI, clockwise.values["arc"]!!, 1e-12)
        assertEquals(PI, clockwise.values["sectorArea"]!!, 1e-12)
        val inferred = one(circle(GeometryVariant.ARC_SECTOR, mapOf("angle" to -90.0, "arc" to PI)))
        assertEquals(2.0, inferred.values["radius"]!!, 1e-12)
        assertTrue(circle(GeometryVariant.ARC_SECTOR, mapOf("radius" to 2.0, "angle" to -361.0)) is GeometrySolveResult.Impossible)
        assertTrue(circle(GeometryVariant.ARC_SECTOR, mapOf("radius" to 2.0, "angle" to 0.0)) is GeometrySolveResult.Impossible)
        assertEquals(4 * PI, one(circle(GeometryVariant.ARC_SECTOR, mapOf("radius" to 2.0, "angle" to -360.0))).values["arc"]!!, 1e-12)
    }

    @Test fun `parallelogram families and constraints`() {
        val general=one(para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"b" to 4.0,"angle" to 90.0)))
        assertEquals(12.0,general.values["area"]!!,1e-12)
        assertEquals(4.0,general.values["heightA"]!!,1e-12)
        assertTrue(para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"b" to 4.0,"angle" to 90.0,"heightA" to 5.0)) is GeometrySolveResult.Impossible)
        assertTrue(para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"b" to 4.0)) is GeometrySolveResult.Insufficient)
        assertEquals(12.0,one(para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"perimeter" to 14.0,"area" to 12.0))).values["area"]!!,1e-12)
        val mirrorEquivalent=para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"b" to 4.0,"area" to 6.0)) as GeometrySolveResult.Solved
        assertEquals(1,mirrorEquivalent.solutions.size)
        val rectangle=para(GeometryVariant.RECTANGLE,mapOf("area" to 12.0,"perimeter" to 14.0)) as GeometrySolveResult.Solved
        assertEquals(1,rectangle.solutions.size)
        val rect=one(para(GeometryVariant.RECTANGLE,mapOf("a" to 3.0,"diagonal1" to 5.0)))
        assertEquals(4.0,rect.values["b"]!!,1e-12)
        assertEquals(90.0,one(para(GeometryVariant.GENERAL,mapOf("a" to 3.0,"b" to 4.0,"diagonal2" to 5.0))).values["angle"]!!,1e-12)
        val rhomb=one(para(GeometryVariant.RHOMBUS,mapOf("diagonal1" to 6.0,"diagonal2" to 8.0)))
        assertEquals(5.0,rhomb.values["a"]!!,1e-12)
        val rhombPerimeter=one(para(GeometryVariant.RHOMBUS,mapOf("angle" to 75.0,"perimeter" to 64.0)))
        assertEquals(16.0,rhombPerimeter.values["a"]!!,1e-12)
        assertEquals(64.0,rhombPerimeter.values["perimeter"]!!,1e-12)
        val rhombArea=one(para(GeometryVariant.RHOMBUS,mapOf("angle" to 75.0,"area" to 256*kotlin.math.sin(Math.toRadians(75.0)))))
        assertEquals(16.0,rhombArea.values["a"]!!,1e-10)
        val perimeterOnly=one(para(GeometryVariant.RHOMBUS,mapOf("perimeter" to 64.0)))
        assertEquals(16.0,perimeterOnly.values["a"]!!,1e-12)
        assertNotNull(perimeterOnly.note)
        assertTrue(para(GeometryVariant.RHOMBUS,mapOf("angle" to 75.0,"perimeter" to 64.0,"a" to 17.0)) is GeometrySolveResult.Impossible)
        assertTrue(para(GeometryVariant.RHOMBUS,mapOf("angle" to 75.0,"perimeter" to 64.0,"area" to 300.0)) is GeometrySolveResult.Impossible)
        val square=one(para(GeometryVariant.SQUARE,mapOf("area" to 16.0)))
        assertEquals(4*sqrt(2.0),square.values["diagonal1"]!!,1e-12)
        assertTrue(para(GeometryVariant.RECTANGLE,mapOf("a" to 3.0,"b" to 4.0,"angle" to 60.0)) is GeometrySolveResult.Impossible)
        assertTrue(para(GeometryVariant.GENERAL,emptyMap()) is GeometrySolveResult.Insufficient)
    }

    @Test fun `parallelogram angle units give equivalent geometry`() {
        val cases = listOf(
            Triple(GeometryVariant.GENERAL, mapOf("a" to 15.0, "b" to 12.0, "angle" to 63.0), mapOf("a" to 15.0, "b" to 12.0, "angle" to Math.toRadians(63.0))),
            Triple(GeometryVariant.RECTANGLE, mapOf("a" to 15.0, "b" to 12.0), mapOf("a" to 15.0, "b" to 12.0)),
            Triple(GeometryVariant.RHOMBUS, mapOf("a" to 16.0, "angle" to 75.0), mapOf("a" to 16.0, "angle" to Math.toRadians(75.0))),
            Triple(GeometryVariant.SQUARE, mapOf("a" to 16.0), mapOf("a" to 16.0))
        )
        cases.forEach { (variant, degrees, radians) ->
            val deg = one(para(variant, degrees, false))
            val rad = one(para(variant, radians, true))
            listOf("a", "b", "area", "perimeter", "diagonal1", "diagonal2", "heightA", "heightB").forEach { key ->
                assertEquals("$variant:$key", deg.values[key]!!, rad.values[key]!!, 1e-9)
            }
            assertEquals(parallelogramAngleRadians(deg.values["angle"]!!, false), parallelogramAngleRadians(rad.values["angle"]!!, true), 1e-12)
        }
        val rectangle = one(para(GeometryVariant.RECTANGLE, mapOf("a" to 15.0, "b" to 12.0)))
        assertEquals(54.0, rectangle.values["perimeter"]!!, 1e-12)
        assertEquals(180.0, rectangle.values["area"]!!, 1e-12)
        assertEquals(sqrt(369.0), rectangle.values["diagonal1"]!!, 1e-12)
        assertEquals(PI/2, parallelogramAngleRadians(PI/2, true), 1e-12)
    }
}
