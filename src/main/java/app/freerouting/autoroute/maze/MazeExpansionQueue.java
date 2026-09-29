package app.freerouting.autoroute.maze;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * The open list of the maze search: a binary heap of {@link MazeListElement}s ordered by {@link
 * MazeListElement#compareTo}.
 *
 * <p>This replaces the {@code TreeSet} used before. The set rejected an element while an element
 * comparing equal to it (same sorting and expansion value, door id and section) was still queued.
 * The heap keeps such duplicates but pops them in insertion order and discards every duplicate that
 * immediately follows the popped element, which yields exactly the same sequence of expanded
 * elements as the set did, without a red-black tree node per queued element.
 */
final class MazeExpansionQueue {

  private static final Comparator<MazeListElement> ORDER =
      (first, second) -> {
        int result = first.compareTo(second);
        if (result != 0) {
          return result;
        }
        return Long.compare(first.queueSequence, second.queueSequence);
      };

  private final PriorityQueue<MazeListElement> heap = new PriorityQueue<>(ORDER);
  private final Predicate<MazeListElement> filter;
  private long nextSequence;

  /** Creates a queue that accepts every element. */
  MazeExpansionQueue() {
    this(null);
  }

  /**
   * Creates a queue that only accepts elements for which {@code filter} returns true. A {@code
   * null} filter accepts every element.
   */
  MazeExpansionQueue(Predicate<MazeListElement> filter) {
    this.filter = filter;
  }

  /** Queues {@code element}. Returns false if the filter rejected it. */
  boolean add(MazeListElement element) {
    if (filter != null && !filter.test(element)) {
      return false;
    }
    element.queueSequence = nextSequence++;
    heap.add(element);
    return true;
  }

  boolean isEmpty() {
    return heap.isEmpty();
  }

  /**
   * Removes and returns the element with the smallest sorting value, or {@code null} if the queue
   * is empty. Elements comparing equal to the returned one are removed as well, because they were
   * queued while it was still queued and the former set semantics would never have admitted them.
   */
  MazeListElement poll() {
    MazeListElement result = heap.poll();
    if (result == null) {
      return null;
    }
    MazeListElement next = heap.peek();
    while (next != null && next.compareTo(result) == 0) {
      heap.poll();
      next = heap.peek();
    }
    return result;
  }
}
