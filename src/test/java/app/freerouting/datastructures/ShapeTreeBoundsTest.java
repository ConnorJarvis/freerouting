package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.RegularTileShape;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The octagon mirror on tree nodes must decide exactly like the polymorphic shape test. */
class ShapeTreeBoundsTest {

  private static RegularTileShape randomShape(Random random) {
    int span = 200;
    int x = random.nextInt(span) - span / 2;
    int y = random.nextInt(span) - span / 2;
    if (random.nextBoolean()) {
      // boxes, including degenerate and empty ones
      return new IntBox(x, y, x + random.nextInt(40) - 5, y + random.nextInt(40) - 5);
    }
    // octagons as produced by union(): not necessarily normalized, possibly empty
    int rx = x + random.nextInt(40) - 5;
    int uy = y + random.nextInt(40) - 5;
    return new IntOctagon(
        x,
        y,
        rx,
        uy,
        x - uy + random.nextInt(9) - 4,
        rx - y + random.nextInt(9) - 4,
        x + y + random.nextInt(9) - 4,
        rx + uy + random.nextInt(9) - 4);
  }

  @Test
  void mirroredBoundsMatchShapeIntersectionForAllShapeCombinations() {
    Random random = new Random(42);
    for (int i = 0; i < 100_000; i++) {
      RegularTileShape nodeShape = randomShape(random);
      RegularTileShape queryShape = randomShape(random);
      ShapeTree.Leaf leaf = new ShapeTree.Leaf(null, 0, null, nodeShape);
      boolean expected = nodeShape.intersects(queryShape);
      IntOctagon queryAsOctagon = ShapeTree.toOctagon(queryShape);
      boolean actual =
          queryShape instanceof IntBox box
              ? leaf.boundsIntersect(box, queryAsOctagon)
              : leaf.boundsIntersect(queryAsOctagon);
      assertEquals(expected, actual, () -> nodeShape + " vs " + queryShape);
    }
  }
}
