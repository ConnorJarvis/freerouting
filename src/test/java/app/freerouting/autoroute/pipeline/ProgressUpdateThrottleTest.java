package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProgressUpdateThrottleTest {

  private static final class FakeClock {
    long now;
  }

  @Test
  void publishesAtMostFourTimesPerSecondWhenUpdatesAreCheap() {
    FakeClock clock = new FakeClock();
    ProgressUpdateThrottle throttle = new ProgressUpdateThrottle(() -> clock.now);

    clock.now = 1_000;
    assertTrue(throttle.shouldUpdate());
    throttle.recordUpdateDuration(5);

    clock.now = 1_200;
    assertFalse(throttle.shouldUpdate(), "200 ms after the last update is too early");

    clock.now = 1_251;
    assertTrue(throttle.shouldUpdate(), "just over 250 ms after the last update is allowed");
    assertEquals(ProgressUpdateThrottle.MIN_INTERVAL_MS, throttle.currentIntervalMs());
  }

  @Test
  void stretchesTheIntervalWhenUpdatesAreExpensive() {
    FakeClock clock = new FakeClock();
    ProgressUpdateThrottle throttle = new ProgressUpdateThrottle(() -> clock.now);

    clock.now = 1_000;
    assertTrue(throttle.shouldUpdate());
    throttle.recordUpdateDuration(300);
    assertEquals(300 * ProgressUpdateThrottle.COST_FACTOR, throttle.currentIntervalMs());

    clock.now = 1_000 + 300 * ProgressUpdateThrottle.COST_FACTOR;
    assertFalse(throttle.shouldUpdate(), "the interval is inclusive of the cost-scaled wait");

    clock.now = 1_001 + 300 * ProgressUpdateThrottle.COST_FACTOR;
    assertTrue(throttle.shouldUpdate());

    // A cheap update afterwards shrinks the interval back to the minimum.
    throttle.recordUpdateDuration(1);
    assertEquals(ProgressUpdateThrottle.MIN_INTERVAL_MS, throttle.currentIntervalMs());
  }

  @Test
  void ignoresNegativeDurations() {
    ProgressUpdateThrottle throttle = new ProgressUpdateThrottle(() -> 0);
    throttle.recordUpdateDuration(-50);
    assertEquals(ProgressUpdateThrottle.MIN_INTERVAL_MS, throttle.currentIntervalMs());
  }
}
