package org.basex.query.value.seq.tree;

import static org.basex.query.QueryError.*;

import java.util.*;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * A tree storing {@link Item}s.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Leo Woerteler
 */
public abstract class TreeSeq extends Seq {
  /** Minimum size of a leaf. */
  static final int MIN_LEAF = 8;
  /** Maximum size of a leaf. */
  static final int MAX_LEAF = 2 * MIN_LEAF - 1;
  /** Minimum number of elements in a digit. */
  static final int MIN_DIGIT = MIN_LEAF / 2;
  /** Maximum number of elements in a digit. */
  static final int MAX_DIGIT = MAX_LEAF + MIN_DIGIT;
  /** Maximum size of a small array. */
  static final int MAX_SMALL = 2 * MIN_DIGIT - 1;

  /**
   * Default constructor.
   * @param size number of elements in this sequence
   * @param type type of all items in this sequence
   */
  TreeSeq(final long size, final Type type) {
    super(size, type == null ? AtomType.ITEM : type);
  }

  @Override
  public final Value insertBefore(final long pos, final Value val) {
    final long n = val.size();
    if(n == 0) return this;
    if(n == 1) return insert(pos, (Item) val);

    final long r = size - pos;
    if(val instanceof TreeSeq && (pos == 0 || r == 0)) {
      final TreeSeq other = (TreeSeq) val;
      return pos == 0 ? other.concat(this) : concat(other);
    }

    final TreeSeqBuilder tsb = new TreeSeqBuilder();
    if(pos < MAX_SMALL) {
      tsb.add(val);
      for(long i = pos; --i >= 0;) tsb.addFront(itemAt(i));
    } else {
      tsb.add(subSeq(0, pos));
      tsb.add(val);
    }

    if(r < MAX_SMALL) {
      for(long i = size - r; i < size; i++) tsb.add(itemAt(i));
    } else {
      tsb.add(subSeq(pos, r));
    }

    return tsb.seq();
  }

  /**
   * Concatenates this sequence with another one.
   * Running time: <i>O(log (min { this.size(), other.size() }))</i>
   * @param other array to append to the end of this array
   * @return resulting array
   */
  public abstract TreeSeq concat(TreeSeq other);

  /**
   * Iterator over the members of this sequence.
   * @param start starting position
   *   (i.e. the position initially returned by {@link ListIterator#nextIndex()})
   * @return array over the array members
   */
  public abstract ListIterator<Item> iterator(long start);

  @Override
  public final Iterator<Item> iterator() {
    return iterator(0);
  }

  @Override
  public abstract BasicIter<Item> iter();

  @Override
  public final void materialize(final InputInfo ii) throws QueryException {
    for(final Item it : this) it.materialize(ii);
  }

  @Override
  public final Seq atomValue(final InputInfo ii) throws QueryException {
    final TreeSeqBuilder tsb = new TreeSeqBuilder();
    for(final Item it : this) tsb.add(it.atomValue(ii));
    return tsb.seq();
  }

  @Override
  public final long atomSize() {
    long s = 0;
    for(final Item it : this) s += it.atomSize();
    return s;
  }

  @Override
  public final int writeTo(final Item[] arr, final int off) {
    final int n = (int) Math.min(arr.length - off, size());
    final Iterator<Item> iter = iterator();
    for(int i = 0; i < n; i++) arr[off + i] = iter.next();
    return n;
  }

  @Override
  public final boolean homogeneous() {
    return homo;
  }

  @Override
  public final boolean iterable() {
    return false;
  }

  @Override
  public final Item ebv(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Item head = itemAt(0);
    if(head instanceof ANode) return head;
    throw EBV_X.get(ii, this);
  }

  /**
   * Prepends the given elements to this sequence.
   * @param vals values, with length at most {@link TreeSeq#MAX_SMALL}
   * @return resulting sequence
   */
  abstract TreeSeq consSmall(Item[] vals);

  /**
   * Returns items containing the values at the indices {@code from} to {@code to - 1} in
   * the given sequence. Its length is always {@code to - from}. If {@code from} is smaller than
   * zero, the first {@code -from} entries in the resulting sequence are {@code null}.
   * If {@code to > arr.length} then the last {@code to - arr.length} entries are {@code null}.
   * If {@code from == 0 && to == arr.length}, the original items are returned.
   * @param items input sequence
   * @param from first index, inclusive (may be negative)
   * @param to last index, exclusive (may be greater than {@code arr.length})
   * @return resulting items
   */
  static Item[] slice(final Item[] items, final int from, final int to) {
    final Item[] out = new Item[to - from];
    final int in0 = Math.max(0, from), in1 = Math.min(to, items.length);
    final int out0 = Math.max(-from, 0);
    System.arraycopy(items, in0, out, out0, in1 - in0);
    return out;
  }

  /**
   * Concatenates the two item arrays.
   * @param as first array
   * @param bs second array
   * @return resulting array
   */
  static Item[] concat(final Item[] as, final Item[] bs) {
    final int l = as.length, r = bs.length, n = l + r;
    final Item[] out = new Item[n];
    System.arraycopy(as, 0, out, 0, l);
    System.arraycopy(bs, 0, out, l, r);
    return out;
  }
}
