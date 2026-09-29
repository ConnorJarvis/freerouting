package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cached contact sets must follow every board change that can alter contacts. */
class TraceContactCacheTest {

  @Test
  void contactsFollowInsertRemoveUndoAndRedo() {
    RoutingBoard board = createBoard();
    PolylineTrace first = insertTrace(board, new IntPoint(100, 100), new IntPoint(200, 100));
    assertTrue(first.getEndContacts().isEmpty());
    long revisionBefore = board.getConnectivityRevision();

    PolylineTrace second = insertTrace(board, new IntPoint(200, 100), new IntPoint(300, 100));
    assertNotEquals(revisionBefore, board.getConnectivityRevision());
    assertEquals(Set.of(second), first.getEndContacts());
    assertEquals(Set.of(first), second.getStartContacts());
    // the cached answer is a copy: mutating it must not leak into later answers
    first.getEndContacts().clear();
    assertEquals(Set.of(second), first.getEndContacts());

    board.generateSnapshot();
    board.removeItem(second);
    assertTrue(first.getEndContacts().isEmpty());

    Set<Integer> changedNets = new HashSet<>();
    assertTrue(board.undo(changedNets));
    assertEquals(1, first.getEndContacts().size());
    Item restored = first.getEndContacts().iterator().next();
    assertEquals(second.getId(), restored.getId());

    assertTrue(board.redo(changedNets));
    assertTrue(first.getEndContacts().isEmpty());
    assertTrue(first.getNormalContacts().isEmpty());
  }

  @Test
  void contactsFollowNetReassignment() {
    RoutingBoard board = createBoard();
    PolylineTrace first = insertTrace(board, new IntPoint(100, 100), new IntPoint(200, 100));
    PolylineTrace second = insertTrace(board, new IntPoint(200, 100), new IntPoint(300, 100));
    assertEquals(Set.of(second), first.getEndContacts());

    second.assignNetNo(2);
    assertTrue(first.getEndContacts().isEmpty());
    second.assignNetNo(1);
    assertEquals(Set.of(second), first.getEndContacts());
  }

  private static PolylineTrace insertTrace(RoutingBoard board, IntPoint from, IntPoint to) {
    return board.insertTraceWithoutCleaning(
        new Polyline(from, to), 0, 10, new int[] {1}, 0, FixedState.UNFIXED);
  }

  private static RoutingBoard createBoard() {
    LayerStructure layerStructure = new LayerStructure(new Layer[] {new Layer("Top", true)});
    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    Communication communication = new Communication();
    communication.observers = null;
    PolylineShape outline = TileShape.getInstance(0, 0, 1000, 1000);
    RoutingBoard board =
        new RoutingBoard(
            new IntBox(0, 0, 1000, 1000),
            layerStructure,
            new PolylineShape[] {outline},
            0,
            boardRules,
            communication);
    boardRules.nets.add("A", 1, false);
    boardRules.nets.add("B", 1, false);
    return board;
  }
}
