package org.basex.query.value.item;

import static org.basex.query.util.Err.*;

import java.math.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.util.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Integer item ({@code xs:int}, {@code xs:integer}, {@code xs:short}, etc.).
 *
 * @author BaseX Team 2005-14, BSD License
 * @author Christian Gruen
 */
public final class Int extends ANum {
  /** Constant values. */
  private static final Int[] NUMS;
  /** Integer value. */
  private final long value;

  // caches the first 128 integers
  static {
    NUMS = new Int[128];
    for(int i = 0; i < NUMS.length; ++i) NUMS[i] = new Int(i);
  }

  /**
   * Constructor.
   * @param value value
   */
  private Int(final long value) {
    this(value, AtomType.ITR);
  }

  /**
   * Constructor.
   * @param value value
   * @param type item type
   */
  public Int(final long value, final Type type) {
    super(type);
    this.value = value;
  }

  /**
   * Returns an instance of this class.
   * @param value value
   * @return instance
   */
  public static Int get(final long value) {
    return value >= 0 && value < NUMS.length ? NUMS[(int) value] : new Int(value);
  }

  /**
   * Returns an instance of this class.
   * @param value value
   * @param type item type
   * @return instance
   */
  public static Int get(final long value, final Type type) {
    return type == AtomType.ITR ? get(value) : new Int(value, type);
  }

  @Override
  public byte[] string() {
    return value == 0 ? Token.ZERO : Token.token(value);
  }

  @Override
  public boolean bool(final InputInfo ii) {
    return value != 0;
  }

  @Override
  public long itr() {
    return value;
  }

  @Override
  public float flt() {
    return value;
  }

  @Override
  public double dbl() {
    return value;
  }

  @Override
  public Item test(final QueryContext qc, final InputInfo ii) {
    return value == qc.pos ? this : null;
  }

  @Override
  public BigDecimal dec(final InputInfo ii) {
    return BigDecimal.valueOf(value);
  }

  @Override
  public Int abs() {
    return value < 0 ? Int.get(-value) : type != AtomType.ITR ? Int.get(value) : this;
  }

  @Override
  public Int ceiling() {
    return this;
  }

  @Override
  public Int floor() {
    return this;
  }

  @Override
  public ANum round(final int scale, final boolean even) {
    if(scale >= 0 || value == 0 && !even) return this;
    if(scale < -15) return Uln.get(BigInteger.valueOf(value)).round(scale, even);

    long f = 1;
    for(long i = 1; i <= -scale; i++) f *= 10;
    final long a = Math.abs(value), m = a % f, d = m << 1;
    long v = a - m;
    if(even) {
      if(d > f) {
        v += f;
      } else if(d == f) {
        if(v % (2 * f) != 0) v += f;
      }
      if(value < 0) v = -v;
    } else {
      if(value > 0) {
        if(d >= f) v += f;
      } else {
        if(d > f) v += f;
        v = -v;
      }
    }
    return v == value ? this : Int.get(v);
  }

  @Override
  public boolean eq(final Item it, final Collation coll, final InputInfo ii)
      throws QueryException {
    return it instanceof Int ? value == ((Int) it).value : value == it.dbl(ii);
  }

  @Override
  public int diff(final Item it, final Collation coll, final InputInfo ii)
      throws QueryException {
    if(it instanceof Int) {
      final long i = ((Int) it).value;
      return value < i ? -1 : value > i ? 1 : 0;
    }
    final double n = it.dbl(ii);
    return Double.isNaN(n) ? UNDEF : value < n ? -1 : value > n ? 1 : 0;
  }

  @Override
  public Object toJava() {
    switch((AtomType) type) {
      case BYT: return (byte) value;
      case SHR:
      case UBY: return (short) value;
      case INT:
      case USH: return (int) value;
      case LNG:
      case UIN: return value;
      default:  return new BigInteger(toString());
    }
  }

  @Override
  public boolean sameAs(final Expr cmp) {
    if(!(cmp instanceof Int)) return false;
    final Int i = (Int) cmp;
    return type == i.type && value == i.value;
  }

  /**
   * Converts the given item into a long value.
   * @param value value to be converted
   * @param ii input info
   * @return long value
   * @throws QueryException query exception
   */
  public static long parse(final byte[] value, final InputInfo ii) throws QueryException {
    final byte[] val = Token.trim(value);
    // fast check for valid characters
    boolean valid = true;
    for(final byte v : val) {
      if(!Token.digit(v) && v != '+' && v != '-') {
        valid = false;
        break;
      }
    }
    // valid: try fast conversion
    if(valid) {
      final long l = Token.toLong(val);
      if(l != Long.MIN_VALUE || Token.eq(val, Token.MINLONG)) return l;
    }
    throw funCastError(ii, AtomType.INT, val);
  }
}
