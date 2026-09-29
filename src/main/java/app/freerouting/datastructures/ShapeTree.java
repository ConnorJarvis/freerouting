package app.freerouting.datastructures;

import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.ShapeBoundingDirections;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract binary search tree for shapes in the plane. The shapes are stored in the leafs of the
 * tree. Objects to be stored in the tree must implement the interface ShapeTree.Storable.
 */
public abstract class ShapeTree {

  /** Fixed directions for calculating bounding regular tile shapes stored in this tree. */
  protected final ShapeBoundingDirections boundingDirections;

  /** Root node - initially null. */
  protected TreeNode root;

  /** The number of entries stored in the tree. */
  protected int leafCount;

  /** Protects the tree structure and the state of Leaves reachable from the tree. */
  private final ReentrantReadWriteLock treeLock = new ReentrantReadWriteLock();

  /**
   * Incremented on every structural change of the tree and on every in-place change of a leaf.
   * Query results cached outside the tree are valid only while this value is unchanged.
   */
  private long modificationCount;

  /** Creates a new instance of ShapeTree. */
  protected ShapeTree(ShapeBoundingDirections directions) {
    boundingDirections = directions;
    root = null;
    leafCount = 0;
  }

  /** Returns the read lock used by this tree hierarchy. */
  protected final Lock readLock() {
    return treeLock.readLock();
  }

  /** Returns the write lock used by this tree hierarchy. */
  protected final Lock writeLock() {
    return treeLock.writeLock();
  }

  /** Returns a counter that changes whenever the tree or one of its leaves changes. */
  public final long getModificationCount() {
    return modificationCount;
  }

  /** Records a change of the tree structure or of a leaf. Called with the write lock held. */
  protected final void markModified() {
    ++modificationCount;
  }

  /** Inserts all shapes of obj into the tree. */
  public void insert(ShapeTree.Storable obj) {
    Lock lock = writeLock();
    lock.lock();
    try {
      int shapeCount = obj.treeShapeCount(this);
      if (shapeCount <= 0) {
        return;
      }
      Leaf[] leafArr = new Leaf[shapeCount];
      for (int i = 0; i < shapeCount; i++) {
        leafArr[i] = insert(obj, i);
      }
      obj.setSearchTreeEntries(leafArr, this);
    } finally {
      lock.unlock();
    }
  }

  /** Insert a shape - creates a new node with a bounding shape. */
  protected Leaf insert(ShapeTree.Storable object, int index) {
    Lock lock = writeLock();
    lock.lock();
    try {
      Shape objectShape = object.getTreeShape(this, index);
      if (objectShape == null) {
        return null;
      }

      RegularTileShape boundingShape = objectShape.boundingShape(boundingDirections);
      if (boundingShape == null) {
        FRLogger.warn("ShapeTree.insert: bounding shape of TreeObject is null");
        return null;
      }
      // Construct a new KdLeaf and set it up
      Leaf newLeaf = new Leaf(object, index, null, boundingShape);
      this.insert(newLeaf);
      return newLeaf;
    } finally {
      lock.unlock();
    }
  }

  abstract void insert(Leaf leaf);

  public abstract void removeLeaf(Leaf leaf);

  /** Inserts the leaves of this tree into an array. */
  public Leaf[] toArray() {
    Lock lock = readLock();
    lock.lock();
    try {
      return toArrayUnlocked();
    } finally {
      lock.unlock();
    }
  }

  /** Inserts the leaves of this tree into an array while the read lock is already held. */
  private Leaf[] toArrayUnlocked() {
    Leaf[] result = new Leaf[this.leafCount];
    if (result.length == 0) {
      return result;
    }
    TreeNode currentNode = this.root;
    int currentIndex = 0;
    for (; ; ) {
      // go down from currentNode to the left most leaf
      while (currentNode instanceof InnerNode) {
        currentNode = ((InnerNode) currentNode).firstChild;
      }
      result[currentIndex] = (Leaf) currentNode;

      ++currentIndex;
      // go up until parent.secondChild != currentNode, which means we came from firstChild
      InnerNode currentParent = currentNode.parent;
      while (currentParent != null && currentParent.secondChild == currentNode) {
        currentNode = currentParent;
        currentParent = currentNode.parent;
      }
      if (currentParent == null) {
        break;
      }
      currentNode = currentParent.secondChild;
    }
    return result;
  }

  /** Removes all entries of obj in the tree. */
  public void remove(Leaf[] entries) {
    Lock lock = writeLock();
    lock.lock();
    try {
      if (entries == null) {
        return;
      }
      for (int i = 0; i < entries.length; i++) {
        removeLeaf(entries[i]);
      }
    } finally {
      lock.unlock();
    }
  }

  /** Returns the number of entries stored in the tree. */
  public int size() {
    Lock lock = readLock();
    lock.lock();
    try {
      return leafCount;
    } finally {
      lock.unlock();
    }
  }

  /** Outputs some statistic information about the tree. */
  public void statistics(String message) {
    Lock lock = readLock();
    lock.lock();
    try {
      Leaf[] leafArr = this.toArrayUnlocked();
      double cumulativeDepth = 0;
      int maximumDepth = 0;
      for (int i = 0; i < leafArr.length; i++) {
        if (leafArr[i] != null) {
          int distanceToRoot = leafArr[i].distanceToRoot();
          cumulativeDepth += distanceToRoot;
          maximumDepth = Math.max(maximumDepth, distanceToRoot);
        }
      }
      double averageDepth = cumulativeDepth / leafArr.length;
      FRLogger.info(
          "MinAreaTree: Entry count: "
              + leafArr.length
              + " log: "
              + Math.round(Math.log(leafArr.length))
              + " Average depth: "
              + Math.round(averageDepth)
              + " "
              + " Maximum depth: "
              + maximumDepth
              + " "
              + message);
    } finally {
      lock.unlock();
    }
  }

  /** Interface, which must be implemented by objects to be stored in a ShapeTree. */
  public interface Storable extends Comparable<Object> {

    /** Number of shapes of an object to store in shapeTree. */
    int treeShapeCount(ShapeTree shapeTree);

    /**
     * Get the Shape of this object with index stored in the ShapeTree with index identification
     * number treeId.
     */
    TileShape getTreeShape(ShapeTree tree, int index);

    /**
     * Stores the entries in the ShapeTrees of this object for better performance while for example
     * deleting tree entries. Called only by insert methods of class ShapeTree.
     */
    void setSearchTreeEntries(Leaf[] entries, ShapeTree tree);
  }

  /** Information of a single object stored in a tree. */
  public static class TreeEntry {

    public final ShapeTree.Storable object;
    public final int shapeIndexInObject;

    /** Creates a tree entry for object and shapeIndexInObject. */
    public TreeEntry(ShapeTree.Storable object, int shapeIndexInObject) {
      this.object = object;
      this.shapeIndexInObject = shapeIndexInObject;
    }
  }

  //////////////////////////////////////////////////////////

  /**
   * Returns {@code shape} as an octagon. A box is converted the same way {@link
   * IntBox#intersects(IntOctagon)} converts it, so octagon intersection tests on the result decide
   * exactly like the polymorphic {@link RegularTileShape#intersects(Shape)} tests would.
   */
  public static IntOctagon toOctagon(RegularTileShape shape) {
    if (shape instanceof IntOctagon octagon) {
      return octagon;
    }
    return ((IntBox) shape).toIntOctagon();
  }

  /** Common functionality of inner nodes and leaf nodes. */
  protected static class TreeNode {

    /**
     * The bounding shape of this node. Always assign it through {@link #setBoundingShape} so the
     * octagon mirror used by the traversals stays in sync.
     */
    public RegularTileShape boundingShape;

    InnerNode parent;

    // The bounding shape as (possibly non-normalized) octagon coordinates. The traversals test
    // these plain fields instead of dispatching through the shape object, which saves a
    // dereference and two virtual calls per visited node on the hottest query path.
    private boolean boundsAreBox;
    private int boundsLeftX;
    private int boundsBottomY;
    private int boundsRightX;
    private int boundsTopY;
    private int boundsUpperLeftDiagonalX;
    private int boundsLowerRightDiagonalX;
    private int boundsLowerLeftDiagonalX;
    private int boundsUpperRightDiagonalX;

    /** Sets the bounding shape and refreshes the octagon mirror. */
    void setBoundingShape(RegularTileShape shape) {
      this.boundingShape = shape;
      if (shape == null) {
        return;
      }
      boundsAreBox = shape instanceof IntBox;
      IntOctagon octagon = toOctagon(shape);
      boundsLeftX = octagon.leftX;
      boundsBottomY = octagon.bottomY;
      boundsRightX = octagon.rightX;
      boundsTopY = octagon.topY;
      boundsUpperLeftDiagonalX = octagon.upperLeftDiagonalX;
      boundsLowerRightDiagonalX = octagon.lowerRightDiagonalX;
      boundsLowerLeftDiagonalX = octagon.lowerLeftDiagonalX;
      boundsUpperRightDiagonalX = octagon.upperRightDiagonalX;
    }

    /**
     * Returns {@code boundingShape.intersects(query)} for a box query. Between two boxes the
     * polymorphic test is {@link IntBox#intersects(IntBox)}, which differs from the octagon test
     * for degenerate boxes, so it is reproduced here; against octagon bounds the box is converted
     * like {@link IntBox#intersects(IntOctagon)} does, which {@code queryAsOctagon} supplies.
     */
    public boolean boundsIntersect(IntBox query, IntOctagon queryAsOctagon) {
      if (!boundsAreBox) {
        return boundsIntersect(queryAsOctagon);
      }
      if (query.ll.x > boundsRightX) {
        return false;
      }
      if (query.ll.y > boundsTopY) {
        return false;
      }
      if (boundsLeftX > query.ur.x) {
        return false;
      }
      return boundsBottomY <= query.ur.y;
    }

    /**
     * Returns {@code boundingShape.intersects(query)} evaluated on the octagon mirror; identical to
     * {@link IntOctagon#intersects(IntOctagon)} on the two octagons.
     */
    public boolean boundsIntersect(IntOctagon query) {
      if (Math.max(query.leftX, boundsLeftX) > Math.min(query.rightX, boundsRightX)) {
        return false;
      }
      if (Math.max(query.bottomY, boundsBottomY) > Math.min(query.topY, boundsTopY)) {
        return false;
      }
      if (Math.max(query.lowerLeftDiagonalX, boundsLowerLeftDiagonalX)
          > Math.min(query.upperRightDiagonalX, boundsUpperRightDiagonalX)) {
        return false;
      }
      return Math.max(query.upperLeftDiagonalX, boundsUpperLeftDiagonalX)
          <= Math.min(query.lowerRightDiagonalX, boundsLowerRightDiagonalX);
    }
  }

  //////////////////////////////////////////////////////////

  /** Description of an inner node of the tree, which implements a fork to its two children. */
  public static class InnerNode extends TreeNode {

    public TreeNode firstChild;
    public TreeNode secondChild;

    /** Creates an inner node with boundingShape and parent. */
    public InnerNode(RegularTileShape boundingShape, InnerNode parent) {
      setBoundingShape(boundingShape);
      this.parent = parent;
      firstChild = null;
      secondChild = null;
    }
  }

  //////////////////////////////////////////////////////////

  /** Description of a leaf of the Tree, where the geometric information is stored. */
  public static class Leaf extends TreeNode implements Comparable<Leaf> {

    /** Actual object stored. */
    public ShapeTree.Storable object;

    /** Index of the shape in the object. */
    public int shapeIndexInObject;

    /** Creates a leaf node for object at index with parent and boundingShape. */
    public Leaf(
        ShapeTree.Storable object, int index, InnerNode parent, RegularTileShape boundingShape) {
      setBoundingShape(boundingShape);
      this.parent = parent;
      this.object = object;
      this.shapeIndexInObject = index;
    }

    @Override
    public int compareTo(Leaf other) {
      int result = this.object.compareTo(other.object);
      if (result == 0) {
        result = shapeIndexInObject - other.shapeIndexInObject;
      }
      return result;
    }

    /** Returns the number of nodes between this leaf and the croot of the tree. */
    public int distanceToRoot() {
      int result = 1;
      InnerNode currentParent = this.parent;
      while (currentParent.parent != null) {
        currentParent = currentParent.parent;
        ++result;
      }
      return result;
    }
  }
}
