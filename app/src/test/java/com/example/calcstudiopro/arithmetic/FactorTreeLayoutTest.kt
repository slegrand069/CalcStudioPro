package com.example.calcstudiopro.arithmetic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import kotlin.math.hypot

class FactorTreeLayoutTest {
    @Test fun documentedFactorizationsKeepHierarchicalGeometry() {
        listOf(12, 60, 360, 1320, 65536).forEach { value ->
            val root = BigInteger.valueOf(value.toLong())
            val tree = buildFactorTree(root, PrimeEngine.factor(root))
            val layout = FactorTreeLayouter.layout(tree)
            val rootPoint = layout.nodes.single { it.node == tree }
            assertEquals(rootPoint.x, layout.width / 2f, 0.05f)
            assertEquals(layout.nodes.size - 1, layout.edges.size)
            layout.nodes.forEach { parent ->
                val children = layout.edges.filter { it.parent.node == parent.node }.map { it.child }
                if (parent.node.prime) assertTrue(children.isEmpty()) else {
                    assertEquals("composite nodes have two children", 2, children.size)
                    assertEquals(parent.x, (children[0].x + children[1].x) / 2f, 0.01f)
                    children.forEach { assertTrue("children are below their parent", it.y > parent.y) }
                }
            }
            for (i in layout.nodes.indices) for (j in i + 1 until layout.nodes.size) {
                val a = layout.nodes[i]; val b = layout.nodes[j]
                if (a.y == b.y) assertTrue("same-level circles do not overlap", hypot(a.x - b.x, a.y - b.y) >= a.radius + b.radius)
            }
            for (i in layout.edges.indices) for (j in i + 1 until layout.edges.size) {
                val first = layout.edges[i]; val second = layout.edges[j]
                if (first.parent.node !== second.parent.node && first.parent.node !== second.child.node && first.child.node !== second.parent.node && first.child.node !== second.child.node) {
                    assertTrue("independent branches do not cross", !segmentsCross(first, second))
                }
            }
            val leaves = layout.nodes.filter { it.node.prime }
            assertEquals(PrimeEngine.factor(root).values.sum(), leaves.size)
            assertEquals(root, leaves.fold(BigInteger.ONE) { product, leaf -> product.multiply(leaf.node.value) })
            assertTrue(layout.height > 0 && layout.width >= rootPoint.radius * 2)
        }
    }
}

private fun segmentsCross(a: FactorTreeEdge, b: FactorTreeEdge): Boolean {
    fun orientation(p: FactorTreePoint, q: FactorTreePoint, r: FactorTreePoint): Float =
        (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val o1 = orientation(a.parent, a.child, b.parent)
    val o2 = orientation(a.parent, a.child, b.child)
    val o3 = orientation(b.parent, b.child, a.parent)
    val o4 = orientation(b.parent, b.child, a.child)
    return o1 * o2 < 0f && o3 * o4 < 0f
}
