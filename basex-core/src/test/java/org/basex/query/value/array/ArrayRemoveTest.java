package org.basex.query.value.array;

import static org.junit.Assert.*;

import java.util.*;

import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.junit.*;

/**
 * Tests the {@link Array#remove(long)} method.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Leo Woerteler
 */
public final class ArrayRemoveTest {
  /** Negative index on empty array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void emptyRemoveNegative() {
    Array.empty().remove(-1);
  }

  /** Zero index on empty array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void emptyRemoveZero() {
    Array.empty().remove(0);
  }

  /** Negative index on singleton array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void singletonRemoveNegative() {
    Array.singleton(Int.get(42)).remove(-1);
  }

  /** Too big index on singleton array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void singletonRemoveOne() {
    Array.singleton(Int.get(42)).remove(1);
  }

  /** Negative index on deep array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void deepRemoveNegative() {
    Array.singleton(Int.ZERO).snoc(Int.ONE).remove(-1);
  }

  /** too big index on deep array. */
  @Test(expected = IndexOutOfBoundsException.class)
  public void deepRemoveTwo() {
    Array.singleton(Int.ZERO).snoc(Int.ONE).remove(2);
  }

  /** Remove one element from singleton array. */
  @Test
  public void singletonTest() {
    final Array singleton = Array.singleton(Int.get(42));
    assertSame(Array.empty(), singleton.remove(0));
  }

  /** Delete each element once from arrays of varying length. */
  @Test
  public void deleteOneTest() {
    final int n = 200;
    Array arr = Array.empty();
    for(int k = 0; k < n; k++) {
      for(int i = 0; i < k; i++) {
        final Array arr2 = arr.remove(i);
        final Iterator<Value> iter = arr2.iterator(0);
        for(int j = 0; j < k - 1; j++) {
          assertTrue(iter.hasNext());
          assertEquals(j < i ? j : j + 1, ((Int) iter.next()).itr());
        }
        assertFalse(iter.hasNext());
      }
      arr = arr.snoc(Int.get(k));
      assertEquals(k + 1, arr.arraySize());
      assertEquals(k, ((Int) arr.last()).itr());
    }
  }

  /** Delete elements so that the middle tree collapses. */
  @Test
  public void collapseMiddleTest() {
    final Array arr = from(0, 1, 2, 3, 4, 5, 6, 7, 8);

    Array arr2 = arr.tail();
    arr2 = arr2.remove(4);
    arr2 = arr2.remove(2);
    assertContains(arr2, 1, 2, 4, 6, 7, 8);

    Array arr3 = arr.cons(Int.get(-1)).snoc(Int.get(9));
    arr3 = arr3.remove(5);
    arr3 = arr3.remove(5);
    assertContains(arr3, -1, 0, 1, 2, 3, 6, 7, 8, 9);

    Array arr4 = arr.cons(Int.get(-1));
    arr4 = arr4.remove(5);
    arr4 = arr4.remove(5);
    assertContains(arr4, -1, 0, 1, 2, 3, 6, 7, 8);
  }

  /** Delete elements so that the left digit is emptied. */
  @Test
  public void emptyLeftDigitTest() {
    Array arr = from(0, 1, 2, 3, 4, 5, 6, 7, 8);
    arr = arr.remove(0);
    arr = arr.remove(0);
    arr = arr.remove(0);
    arr = arr.remove(0);
    assertContains(arr, 4, 5, 6, 7, 8);
  }

  /** Delete elements so that the right digit is emptied. */
  @Test
  public void emptyRightDigitTest() {
    Array arr = from(0, 1, 2, 3, 4, 5, 6, 7, 8);
    arr = arr.remove(8);
    arr = arr.remove(7);
    arr = arr.remove(6);
    arr = arr.remove(5);
    assertContains(arr, 0, 1, 2, 3, 4);

    Array arr2 = from(1, 2, 3, 4, 5, 6, 7, 8, 9).cons(Int.ZERO);
    for(int i = 9; i >= 4; i--) {
      arr2 = arr2.remove(i);
    }
    assertContains(arr2, 0, 1, 2, 3);
  }

  /** Delete in the left digit of a deep node. */
  @Test
  public void deepLeftTest() {
    Array arr = from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    arr = arr.remove(3);
    assertContains(arr, 0, 1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);

    Array arr2 = from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    arr2 = arr2.remove(6);
    assertContains(arr2, 0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15);

    Array arr3 = from(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24);
    arr3 = arr3.remove(9);
    arr3 = arr3.remove(6);
    arr3 = arr3.remove(5);
    arr3 = arr3.remove(4);
    arr3 = arr3.remove(3);
    arr3 = arr3.remove(3);
    assertContains(arr3,
        0, 1, 2, 8, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24);

    Array arr4 = from(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24);
    arr4 = arr4.remove(6);
    arr4 = arr4.remove(5);
    arr4 = arr4.remove(4);
    arr4 = arr4.remove(3);
    arr4 = arr4.remove(3);
    assertContains(arr4,
        0, 1, 2, 8, 9, 10, 11, 12, 13, 14,
        15, 16, 17, 18, 19, 20, 21, 22, 23, 24);

    Array arr5 = from(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24);
    arr5 = arr5.remove(17);
    arr5 = arr5.remove(16);
    arr5 = arr5.remove(15);
    arr5 = arr5.remove(6);
    arr5 = arr5.remove(5);
    arr5 = arr5.remove(4);
    arr5 = arr5.remove(3);
    arr5 = arr5.remove(3);
    assertContains(arr5, 0, 1, 2, 8, 9, 10, 11, 12, 13, 14, 18, 19, 20, 21, 22, 23, 24);

    Array arr6 = from(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
        10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21
    );
    for(int i = 12; i >= 4; i--) {
      arr6 = arr6.remove(i);
    }
    assertContains(arr6, 0, 1, 2, 3, 13, 14, 15, 16, 17, 18, 19, 20, 21);
  }

  /** Delete in the middle tree of a deep node. */
  @Test
  public void deepMiddleTest() {
    Array arr = from(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
        10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26);

    for(int i = 8; i >= 6; i--) {
      arr = arr.remove(i);
    }

    for(int i = 15; i >= 9; i--) {
      arr = arr.remove(i - 3);
    }

    assertContains(arr, 0, 1, 2, 3, 4, 5, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26);

    Array arr2 = from(
        5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
        20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30);
    for(int i = 4; i >= 0; i--) arr2 = arr2.cons(Int.get(i));
    for(int i = 31; i <= 35; i++) arr2 = arr2.snoc(Int.get(i));
    for(int i = 22; i >= 16; i--) arr2 = arr2.remove(i);
    assertContains(arr2, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13,
            14, 15, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35);
  }

  /** Delete in the right digit of a deep node. */
  @Test
  public void deepRightTest() {
    Array arr = from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
    for(int i = 12; i >= 8; i--) arr = arr.remove(i);
    arr = arr.remove(8);
    assertContains(arr, 0, 1, 2, 3, 4, 5, 6, 7, 14, 15, 16, 17);

    Array arr2 = from(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
    for(int i = 12; i >= 9; i--) arr2 = arr2.remove(i);
    arr2 = arr2.remove(9);
    assertContains(arr2, 0, 1, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17);
  }

  /**
   * Randomly delete elements until an array is empty.
   */
  @Test
  public void fuzzyTest() {
    final int n = 20_000;
    final ArrayList<Value> list = new ArrayList<>(n);
    for(int i = 0; i < n; i++) list.add(Int.get(i));

    Array arr = Array.from(list.toArray(new Value[list.size()]));

    final Random rng = new Random(42);
    for(int i = 0; i < n; i++) {
      final int delPos = rng.nextInt(n - i);
      list.remove(delPos);
      arr = arr.remove(delPos);
      final int size = n - i - 1;
      assertEquals(size, arr.arraySize());
      assertEquals(size, list.size());

      if(i % 1000 == 999) {
        arr.checkInvariants();
        for(int j = 0; j < size; j++) {
          assertEquals(((Int) list.get(j)).itr(), ((Int) arr.get(j)).itr());
        }
      }
    }
  }

  /**
   * Creates an array containing {@link Int} instances representing the given integers.
   * @param vals values in the array
   * @return the array
   */
  private static Array from(final int... vals) {
    final ArrayBuilder builder = new ArrayBuilder();
    for(final int v : vals) builder.append(Int.get(v));
    return builder.freeze();
  }

  /**
   * Checks that the given array contains the given integers.
   * @param arr array to check the contents of
   * @param vals integers to look for
   * @throws AssertionError of the check fails
   */
  private static void assertContains(final Array arr, final int... vals) {
    final Iterator<Value> iter = arr.iterator(0);
    for(final int v : vals) {
      assertTrue(iter.hasNext());
      assertEquals(v, ((Int) iter.next()).itr());
    }
    assertFalse(iter.hasNext());
  }
}
