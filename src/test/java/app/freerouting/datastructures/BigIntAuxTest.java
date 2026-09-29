package app.freerouting.datastructures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BigIntAuxTest {

  private static long unsignedGcd(int a, int b) {
    return BigInteger.valueOf(Integer.toUnsignedLong(a))
        .gcd(BigInteger.valueOf(Integer.toUnsignedLong(b)))
        .longValueExact();
  }

  @Test
  void binaryGcdMatchesBigIntegerOnUnsignedValues() {
    Random random = new Random(20260929);
    int[] specials = {0, 1, 2, 3, 6, 8, 12, 1 << 24, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, -6};
    for (int a : specials) {
      for (int b : specials) {
        assertEquals(unsignedGcd(a, b), Integer.toUnsignedLong(BigIntAux.binaryGcd(a, b)));
      }
    }
    for (int i = 0; i < 200_000; i++) {
      int a = random.nextInt();
      int b = random.nextInt();
      if (i % 2 == 0) {
        // coordinate-sized values, the common case in the geometry code
        a = random.nextInt(1 << 26) - (1 << 25);
        b = random.nextInt(1 << 26) - (1 << 25);
      }
      assertEquals(
          unsignedGcd(a, b),
          Integer.toUnsignedLong(BigIntAux.binaryGcd(a, b)),
          () -> "gcd mismatch for " + Integer.toUnsignedString(1) + " inputs");
    }
  }
}
