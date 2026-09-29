package app.freerouting.board.model.items;

import app.freerouting.datastructures.ShapeTree;
import app.freerouting.logger.FRLogger;
import java.util.List;
import java.util.Set;

/**
 * A contact set together with the board connectivity revision it was computed at. Immutable, so a
 * cache field holding it can be replaced atomically.
 *
 * @param revision the value of {@code BasicBoard.getConnectivityRevision()} at computation time
 * @param contacts the contacts; callers copy before handing the set out
 * @param consulted the tree entries whose tree shapes the spatial query looked up, in query order
 */
record CachedContacts(long revision, Set<Item> contacts, List<ShapeTree.TreeEntry> consulted) {

  /**
   * Repeats the tree shape lookups the original query performed. {@code Item.getTreeShape}
   * recomputes an item's derived data when its precalculated shapes are missing or out of step with
   * its tree entries, which also resets the item's autoroute information. A cached answer has to
   * trigger the same recomputations at the same moments, otherwise later routing decisions (room
   * identifiers and thereby tie-breaks) would differ from an uncached run.
   */
  void replayTreeShapeLookups(ShapeTree tree) {
    for (ShapeTree.TreeEntry entry : consulted) {
      entry.object.getTreeShape(tree, entry.shapeIndexInObject);
    }
  }

  /**
   * When the system property {@code freerouting.contacts.verify} is true, every cache hit is
   * checked against a fresh query. Diagnostic aid for finding board changes that escape the
   * connectivity revision; off in production.
   */
  static final boolean VERIFY = Boolean.getBoolean("freerouting.contacts.verify");

  /**
   * Diagnostic bisection of the query's side effects: with {@code freerouting.contacts.touch} set
   * to {@code query} every cache hit additionally runs the spatial query and discards it, with
   * {@code dfs} it only runs the search-tree traversal.
   */
  static final String TOUCH = System.getProperty("freerouting.contacts.touch", "");

  private static final java.util.concurrent.atomic.AtomicInteger CONSULT_REPORTS =
      new java.util.concurrent.atomic.AtomicInteger();

  /** Diagnostic: compares the consulted entries of a fresh query with the recorded ones. */
  void verifyConsulted(Item owner, List<ShapeTree.TreeEntry> fresh) {
    boolean same = fresh.size() == consulted.size();
    for (int i = 0; same && i < fresh.size(); i++) {
      same =
          fresh.get(i).object == consulted.get(i).object
              && fresh.get(i).shapeIndexInObject == consulted.get(i).shapeIndexInObject;
    }
    if (same || CONSULT_REPORTS.incrementAndGet() > 20) {
      return;
    }
    StringBuilder message =
        new StringBuilder("Consulted entries changed for ")
            .append(owner.getClass().getSimpleName())
            .append(" #")
            .append(owner.getId())
            .append(" at revision ")
            .append(revision)
            .append(": cached=");
    describeEntries(consulted, message);
    message.append(" fresh=");
    describeEntries(fresh, message);
    FRLogger.error(message.toString(), new IllegalStateException("consulted entries changed"));
  }

  private static void describeEntries(List<ShapeTree.TreeEntry> entries, StringBuilder into) {
    into.append('[');
    for (ShapeTree.TreeEntry entry : entries) {
      into.append(entry.object.getClass().getSimpleName());
      if (entry.object instanceof Item item) {
        into.append('#').append(item.getId());
      }
      into.append('/').append(entry.shapeIndexInObject).append(' ');
    }
    into.append(']');
  }

  void verify(Item owner, Set<Item> fresh) {
    if (contacts.equals(fresh)) {
      return;
    }
    FRLogger.error(
        "Stale contact cache on "
            + owner.getClass().getSimpleName()
            + " #"
            + owner.getId()
            + " at revision "
            + revision
            + ": cached="
            + describe(contacts)
            + " fresh="
            + describe(fresh),
        new IllegalStateException("stale contact cache"));
  }

  private static String describe(Set<Item> items) {
    StringBuilder result = new StringBuilder("[");
    for (Item item : items) {
      if (result.length() > 1) {
        result.append(',');
      }
      result.append(item.getClass().getSimpleName()).append('#').append(item.getId());
    }
    return result.append(']').toString();
  }
}
