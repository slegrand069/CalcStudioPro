package com.example.calcstudiopro.arithmetic

import kotlin.math.max

/** Pure layout, measured in dp so it is independently testable and contains final coordinates. */
data class FactorTreePoint(val node: FactorTreeNode, val x: Float, val y: Float, val radius: Float)
data class FactorTreeEdge(val parent: FactorTreePoint, val child: FactorTreePoint)
data class FactorTreeLayout(val width: Float, val height: Float, val nodes: List<FactorTreePoint>, val edges: List<FactorTreeEdge>)

object FactorTreeLayouter {
    private data class Measure(val node: FactorTreeNode, val width: Float, val radius: Float, val left: Measure? = null, val right: Measure? = null) {
        val height: Int get() = 1 + max(left?.height ?: 0, right?.height ?: 0)
    }
    private data class Positioned(val root: FactorTreePoint, val nodes: List<FactorTreePoint>, val edges: List<FactorTreeEdge>)

    fun layout(root: FactorTreeNode, horizontalGap: Float = 18f, levelGap: Float = 30f, sidePadding: Float = 12f): FactorTreeLayout {
        require(horizontalGap >= 0 && levelGap >= 0 && sidePadding >= 0)
        fun measure(node: FactorTreeNode): Measure {
            // Keep enough room for an 11sp label while avoiding the oversized nodes
            // that made the tree unnecessarily wide on a phone.
            val radius = max(17f, node.value.toString().length * 3.4f + 7f)
            val left = node.left?.let(::measure)
            val right = node.right?.let(::measure)
            val childrenWidth = if (left != null && right != null) left.width + horizontalGap + right.width else 0f
            return Measure(node, max(radius * 2, childrenWidth), radius, left, right)
        }
        val measured = measure(root)
        val rowStep = measured.nodesMaxRadius() * 2 + levelGap
        fun place(m: Measure, x: Float, y: Float): Positioned {
            val own = FactorTreePoint(m.node, x, y, m.radius)
            if (m.left == null || m.right == null) return Positioned(own, listOf(own), emptyList())
            val childCenterDistance = (m.left.width + m.right.width) / 2f + horizontalGap
            val left = place(m.left, x - childCenterDistance / 2f, y + rowStep)
            val right = place(m.right, x + childCenterDistance / 2f, y + rowStep)
            val edges = listOf(FactorTreeEdge(own, left.root), FactorTreeEdge(own, right.root)) + left.edges + right.edges
            return Positioned(own, listOf(own) + left.nodes + right.nodes, edges)
        }
        val positioned = place(measured, measured.width / 2f, measured.nodesMaxRadius() + sidePadding)
        val minX = positioned.nodes.minOf { it.x - it.radius }
        val maxX = positioned.nodes.maxOf { it.x + it.radius }
        val shift = sidePadding + (maxX - minX) / 2f - positioned.root.x
        val nodes = positioned.nodes.map { it.copy(x = it.x + shift) }
        val byNode = nodes.associateBy { it.node }
        val edges = positioned.edges.map { FactorTreeEdge(byNode.getValue(it.parent.node), byNode.getValue(it.child.node)) }
        val depth = measured.height
        val maxRadius = nodes.maxOf { it.radius }
        return FactorTreeLayout(maxX - minX + sidePadding * 2, maxRadius * 2 + (depth - 1) * rowStep + sidePadding * 2, nodes, edges)
    }

    private fun Measure.nodesMaxRadius(): Float = max(radius, max(left?.nodesMaxRadius() ?: 0f, right?.nodesMaxRadius() ?: 0f))
}




