package app.freerouting.autoroute.pipeline;

import java.util.function.LongSupplier;

/**
 * Decides when a routing stage may publish a progress update (board statistics plus board-updated
 * event) to its listeners.
 *
 * <p>Progress updates are not free: each one recomputes the ratsnest for the whole board and lets
 * listeners serialise the board (for example the API job output). On large boards a single update
 * can cost hundreds of milliseconds, and publishing on a fixed schedule then spends more time
 * reporting progress than routing. The throttle therefore adapts: it never publishes more often
 * than {@link #MIN_INTERVAL_MS}, and after each update it waits at least {@link #COST_FACTOR} times
 * as long as that update took, which bounds the reporting overhead to roughly {@code 1 /
 * COST_FACTOR} of the stage's wall time.
 */
final class ProgressUpdateThrottle {

  /**
   * Publish at most four times per second, as before the adaptive throttle existed. The system
   * property {@code freerouting.progress.min_interval_ms} overrides it, mainly so that parity runs
   * can switch progress updates off (any very large value) and compare boards without them.
   */
  static final long MIN_INTERVAL_MS = Long.getLong("freerouting.progress.min_interval_ms", 250L);

  /** Spend at most about one tenth of the wall time on progress reporting. */
  static final long COST_FACTOR = 10;

  private final LongSupplier clockMillis;
  private long lastUpdateTimestampMs;
  private long lastUpdateDurationMs;

  ProgressUpdateThrottle() {
    this(System::currentTimeMillis);
  }

  ProgressUpdateThrottle(LongSupplier clockMillis) {
    this.clockMillis = clockMillis;
  }

  /**
   * Returns true if an update may be published now and records the current time as the last
   * publication time.
   */
  boolean shouldUpdate() {
    long now = clockMillis.getAsLong();
    if (now - lastUpdateTimestampMs > currentIntervalMs()) {
      lastUpdateTimestampMs = now;
      return true;
    }
    return false;
  }

  /** Records how long the last published update took, including listener work. */
  void recordUpdateDuration(long durationMs) {
    lastUpdateDurationMs = Math.max(0, durationMs);
  }

  /** The minimum time that has to pass between two published updates. */
  long currentIntervalMs() {
    return Math.max(MIN_INTERVAL_MS, lastUpdateDurationMs * COST_FACTOR);
  }
}
