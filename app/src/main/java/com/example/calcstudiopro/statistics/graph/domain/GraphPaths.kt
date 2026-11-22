package com.example.calcstudiopro.statistics.graph.domain

import com.example.calcstudiopro.statistics.graph.model.GraphPoint

object GraphPaths {
    /** Splits on non-finite coordinates and explicit breaks; no path can bridge a gap. */
    fun connectedSegments(points: List<GraphPoint>): List<List<GraphPoint>> {
        val result = mutableListOf<List<GraphPoint>>()
        var current = mutableListOf<GraphPoint>()
        fun finish() {
            if (current.isNotEmpty()) result += current.toList()
            current = mutableListOf()
        }
        points.forEach { point ->
            if (!point.x.isFinite() || !point.y.isFinite()) {
                finish()
            } else {
                if (current.isNotEmpty() && !point.connectFromPrevious) finish()
                current += point
            }
        }
        finish()
        return result
    }
}
