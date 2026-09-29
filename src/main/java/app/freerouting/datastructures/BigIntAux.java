package app.freerouting.datastructures;

import java.math.BigInteger;

/** Auxiliary functions with BigInteger parameters. */
public final class BigIntAux {

  private BigIntAux() {
    // disallow instantiation
  }

  /** Calculates the determinant of the vectors (x1, y1) and (x2, y2). */
  public static BigInteger determinant(BigInteger x1, BigInteger y1, BigInteger x2, BigInteger y2) {
    BigInteger tmp1 = x1.multiply(y2);
    BigInteger tmp2 = x2.multiply(y1);
    return tmp1.subtract(tmp2);
  }

  /**
   * Auxiliary function to implement addition and translation in the classes RationalVector and
   * RationalPoint.
   */
  public static BigInteger[] addRationalCoordinates(BigInteger[] first, BigInteger[] second) {
    BigInteger[] result = new BigInteger[3];
    if (first[2].equals(second[2])) {
      // both rational numbers have the same denominator
      result[2] = first[2];
      result[0] = first[0].add(second[0]);
      result[1] = first[1].add(second[1]);
    } else {
      // multiply both denominators for the new denominator
      // to be on the save side:
      // taking the least common multiple would be optimal
      result[2] = first[2].multiply(second[2]);
      BigInteger tmp1 = first[0].multiply(second[2]);
      BigInteger tmp2 = second[0].multiply(first[2]);
      result[0] = tmp1.add(tmp2);
      tmp1 = first[1].multiply(second[2]);
      tmp2 = second[1].multiply(first[2]);
      result[1] = tmp1.add(tmp2);
    }
    return result;
  }

  /** Calculate GCD of a and b interpreted as unsigned integers. */
  public static int binaryGcd(int a, int b) {
    if (b == 0) {
      return a;
    }
    if (a == 0) {
      return b;
    }
    // Stein's algorithm on unsigned values, using the hardware trailing-zero count instead of the
    // byte-table loops of the original java.math copy. The greatest common divisor is unique, so
    // the result is the same.
    int commonShift = Integer.numberOfTrailingZeros(a | b);
    a >>>= Integer.numberOfTrailingZeros(a);
    do {
      b >>>= Integer.numberOfTrailingZeros(b);
      if (Integer.compareUnsigned(a, b) > 0) {
        int swap = a;
        a = b;
        b = swap;
      }
      b -= a;
    } while (b != 0);
    return a << commonShift;
  }
}
