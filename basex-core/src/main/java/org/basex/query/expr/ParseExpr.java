package org.basex.query.expr;

import static org.basex.query.QueryError.*;
import static org.basex.query.QueryText.*;
import static org.basex.util.Token.*;

import java.util.*;

import org.basex.query.*;
import org.basex.query.iter.*;
import org.basex.query.util.*;
import org.basex.query.value.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.query.value.map.Map;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Abstract parse expression. All non-value expressions are derived from this class.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public abstract class ParseExpr extends Expr {
  /** Expression type. */
  public final ExprType exprType;
  /** Input information. */
  public InputInfo info;

  /**
   * Constructor.
   * @param info input info (can be {@code null}
   * @param seqType sequence type
   */
  protected ParseExpr(final InputInfo info, final SeqType seqType) {
    this.info = info;
    this.exprType = new ExprType(seqType);
  }

  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    final Item it = item(qc, info);
    return it != null ? it.iter() : Empty.ITER;
  }

  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Iter iter = iter(qc);
    final Item it = iter.next();
    // check next item if size of iterator is larger than one or unknown (-1)
    if(it != null && iter.size() != 1) {
      final Item nx = iter.next();
      if(nx != null) {
        final ValueBuilder vb = new ValueBuilder().add(it).add(nx);
        if(iter.next() != null) vb.add(Str.get(DOTS));
        throw SEQFOUND_X.get(info, vb.value());
      }
    }
    return it;
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    if(seqType().zeroOrOne()) {
      final Value v = item(qc, info);
      return v == null ? Empty.SEQ : v;
    }
    return qc.iter(this).value(qc);
  }

  @Override
  public Value atomValue(final QueryContext qc, final InputInfo ii) throws QueryException {
    return qc.value(this).atomValue(info);
  }

  @Override
  public final Item atomItem(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Item it = item(qc, info);
    return it == null ? null : it.atomItem(info);
  }

  @Override
  public final Item ebv(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Item it;
    if(seqType().zeroOrOne()) {
      it = item(qc, info);
    } else {
      final Iter iter = iter(qc);
      it = iter.next();
      if(it != null && !(it instanceof ANode)) {
        final Item n = iter.next();
        if(n != null) {
          final ValueBuilder vb = new ValueBuilder().add(it).add(n);
          if(iter.next() != null) vb.add(Str.get(DOTS));
          throw EBV_X.get(info, vb.value());
        }
      }
    }
    return it == null ? Bln.FALSE : it;
  }

  @Override
  public final Item test(final QueryContext qc, final InputInfo ii) throws QueryException {
    final Item it = ebv(qc, info);
    return (it instanceof ANum ? it.dbl(info) == qc.focus.pos : it.bool(info)) ? it : null;
  }

  @Override
  public final SeqType seqType() {
    return exprType.seqType();
  }

  @Override
  public final long size() {
    return exprType.size();
  }

  // OPTIMIZATIONS ================================================================================

  /**
   * Copies this expression's type to the specified expression.
   * @param <T> expression type
   * @param ex expression
   * @return specified expression
   */
  protected final <T extends ParseExpr> T copyType(final T ex) {
    ex.exprType.assign(this);
    return ex;
  }

  /**
   * Assigns the type from the specified expression.
   * @param ex expression
   * @return self reference
   */
  public final ParseExpr adoptType(final Expr ex) {
    exprType.assign(ex);
    return this;
  }

  // VALIDITY CHECKS ==============================================================================

  /**
   * Ensures that the specified expression performs no updates.
   * Otherwise, throws an exception.
   * @param expr expression (may be {@code null})
   * @throws QueryException query exception
   */
  protected void checkNoUp(final Expr expr) throws QueryException {
    if(expr == null) return;
    expr.checkUp();
    if(expr.has(Flag.UPD)) throw UPNOT_X.get(info, description());
  }

  /**
   * Ensures that none of the specified expressions performs an update.
   * Otherwise, throws an exception.
   * @param exprs expressions (may be {@code null}, and may contain {@code null} references)
   * @throws QueryException query exception
   */
  protected final void checkNoneUp(final Expr... exprs) throws QueryException {
    if(exprs == null) return;
    checkAllUp(exprs);
    for(final Expr expr : exprs) {
      if(expr != null && expr.has(Flag.UPD)) throw UPNOT_X.get(info, description());
    }
  }

  /**
   * Ensures that all specified expressions are vacuous or either updating or non-updating.
   * Otherwise, throws an exception.
   * @param exprs expressions to be checked
   * @throws QueryException query exception
   */
  void checkAllUp(final Expr... exprs) throws QueryException {
    // updating state: 0 = initial state, 1 = updating, -1 = non-updating
    int s = 0;
    for(final Expr expr : exprs) {
      expr.checkUp();
      if(expr.isVacuous()) continue;
      final boolean u = expr.has(Flag.UPD);
      if(u ? s == -1 : s == 1) throw UPALL.get(info);
      s = u ? 1 : -1;
    }
  }

  /**
   * Returns the current context value or throws an exception if the context value is not set.
   * @param qc query context
   * @return context value
   * @throws QueryException query exception
   */
  protected final Value ctxValue(final QueryContext qc) throws QueryException {
    final Value v = qc.focus.value;
    if(v != null) return v;
    throw NOCTX_X.get(info, this);
  }

  // CONVERSIONS ==================================================================================

  /**
   * Checks if the specified expression yields a string.
   * Returns a value as token or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return token
   * @throws QueryException query exception
   */
  protected final byte[] toToken(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.atomItem(qc, info);
    if(it == null) throw EMPTYFOUND_X.get(info, AtomType.STR);
    return toToken(it);
  }

  /**
   * Checks if the specified expression yields a string or an empty sequence.
   * Returns a value as token or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return token (empty string if result is an empty sequence)
   * @throws QueryException query exception
   */
  protected final byte[] toEmptyToken(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.atomItem(qc, info);
    return it == null ? EMPTY : toToken(it);
  }

  /**
   * Checks if the specified expression yields a string or an empty sequence.
   * Returns a value as token or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return token (empty string if result is an empty sequence)
   * @throws QueryException query exception
   */
  protected final byte[] toTokenOrNull(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.atomItem(qc, info);
    return it == null ? null : toToken(it);
  }

  /**
   * Checks if the specified non-empty item is a string.
   * Returns its value as token or throws an exception.
   * @param it item to be checked
   * @return token
   * @throws QueryException query exception
   */
  protected final byte[] toToken(final Item it) throws QueryException {
    final Type ip = it.type;
    if(ip.isStringOrUntyped()) return it.string(info);
    throw it instanceof FItem ? FIATOM_X.get(info, it.type) : castError(it, AtomType.STR, info);
  }

  /**
   * Checks if the specified expression yields a boolean.
   * Returns the boolean or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return boolean
   * @throws QueryException query exception
   */
  protected final boolean toBoolean(final Expr ex, final QueryContext qc) throws QueryException {
    return toBoolean(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified item is a boolean.
   * Returns the boolean or throws an exception.
   * @param it item be checked
   * @return boolean
   * @throws QueryException query exception
   */
  protected final boolean toBoolean(final Item it) throws QueryException {
    final Type ip = checkNoEmpty(it, AtomType.BLN).type;
    if(ip == AtomType.BLN) return it.bool(info);
    if(ip.isUntyped()) return Bln.parse(it.string(info), info);
    throw castError(it, AtomType.BLN, info);
  }


  /**
   * Checks if the specified expression yields a double.
   * Returns the double or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return double
   * @throws QueryException query exception
   */
  protected final double toDouble(final Expr ex, final QueryContext qc) throws QueryException {
    return toDouble(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified item is a double.
   * Returns the double or throws an exception.
   * @param it item
   * @return double
   * @throws QueryException query exception
   */
  protected final double toDouble(final Item it) throws QueryException {
    if(checkNoEmpty(it, AtomType.DBL).type.isNumberOrUntyped()) return it.dbl(info);
    throw numberError(this, it);
  }

  /**
   * Checks if the specified expression yields a number or {@code null}.
   * Returns the number, {@code null}, or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return double
   * @throws QueryException query exception
   */
  protected final ANum toNumber(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.atomItem(qc, info);
    return it == null ? null : toNumber(it);
  }

  /**
   * Checks if the specified, non-empty item is a double.
   * Returns the double or throws an exception.
   * @param it item to be checked
   * @return number
   * @throws QueryException query exception
   */
  protected ANum toNumber(final Item it) throws QueryException {
    if(it.type.isUntyped()) return Dbl.get(it.dbl(info));
    if(it instanceof ANum) return (ANum) it;
    throw numberError(this, it);
  }

  /**
   * Checks if the specified expression yields a float.
   * Returns the float or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return float
   * @throws QueryException query exception
   */
  protected final float toFloat(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.atomItem(qc, info);
    if(checkNoEmpty(it, AtomType.FLT).type.isNumberOrUntyped()) return it.flt(info);
    throw numberError(this, it);
  }

  /**
   * Checks if the specified expression yields an integer.
   * Returns a token representation or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return integer value
   * @throws QueryException query exception
   */
  protected final long toLong(final Expr ex, final QueryContext qc) throws QueryException {
    return toLong(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified item is a number.
   * Returns a token representation or throws an exception.
   * @param it item to be checked
   * @return number
   * @throws QueryException query exception
   */
  protected final long toLong(final Item it) throws QueryException {
    final Type ip = checkNoEmpty(it, AtomType.ITR).type;
    if(ip.instanceOf(AtomType.ITR) || ip.isUntyped()) return it.itr(info);
    throw castError(it, AtomType.ITR, info);
  }

  /**
   * Checks if the specified expression yields a node.
   * Returns the boolean or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return node
   * @throws QueryException query exception
   */
  protected final ANode toNode(final Expr ex, final QueryContext qc) throws QueryException {
    return toNode(checkNoEmpty(ex.item(qc, info), NodeType.NOD));
  }

  /**
   * Checks if the specified expression yields a node or {@code null}.
   * Returns the node, {@code null}, or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return node or {@code null}
   * @throws QueryException query exception
   */
  protected final ANode toEmptyNode(final Expr ex, final QueryContext qc) throws QueryException {
    final Item it = ex.item(qc, info);
    return it == null ? null : toNode(it);
  }

  /**
   * Checks if the specified non-empty item is a node.
   * Returns the node or throws an exception.
   * @param it item to be checked
   * @return node
   * @throws QueryException query exception
   */
  protected final ANode toNode(final Item it) throws QueryException {
    if(it instanceof ANode) return (ANode) it;
    throw castError(it, NodeType.NOD, info);
  }

  /**
   * Checks if the specified item is a node or {@code null}.
   * Returns the node, {@code null}, or throws an exception.
   * @param it item to be checked
   * @return node or {@code null}
   * @throws QueryException query exception
   */
  protected final ANode toEmptyNode(final Item it) throws QueryException {
    return it == null ? null : toNode(it);
  }

  /**
   * Checks if the evaluated expression yields a non-empty item.
   * Returns the item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return item
   * @throws QueryException query exception
   */
  protected final Item toItem(final Expr ex, final QueryContext qc) throws QueryException {
    return checkNoEmpty(ex.item(qc, info));
  }

  /**
   * Checks if the specified expression yields a non-empty item.
   * Returns the item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @param type expected type
   * @return item
   * @throws QueryException query exception
   */
  protected final Item toItem(final Expr ex, final QueryContext qc, final Type type)
      throws QueryException {
    return checkNoEmpty(ex.item(qc, info), type);
  }

  /**
   * Checks if the evaluated expression yields a non-empty item.
   * Returns the atomized item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return atomized item
   * @throws QueryException query exception
   */
  protected final Item toAtomItem(final Expr ex, final QueryContext qc) throws QueryException {
    return checkNoEmpty(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified expression yields an element.
   * Returns the element or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return binary item
   * @throws QueryException query exception
   */
  protected final ANode toElem(final Expr ex, final QueryContext qc) throws QueryException {
    return (ANode) checkType(ex.item(qc, info), NodeType.ELM);
  }

  /**
   * Checks if the specified expression yields a binary item.
   * Returns the binary item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return binary item
   * @throws QueryException query exception
   */
  protected final Bin toBin(final Expr ex, final QueryContext qc) throws QueryException {
    return toBin(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified item is a binary item.
   * Returns the binary item or throws an exception.
   * @param it item to be checked
   * @return binary item
   * @throws QueryException query exception
   */
  protected Bin toBin(final Item it) throws QueryException {
    if(checkNoEmpty(it) instanceof Bin) return (Bin) it;
    throw BINARY_X.get(info, it.type);
  }

  /**
   * Checks if the specified expression yields a string or binary item.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return byte array
   * @throws QueryException query exception
   */
  protected final byte[] toBytes(final Expr ex, final QueryContext qc) throws QueryException {
    return toBytes(ex.atomItem(qc, info));
  }

  /**
   * Checks if the specified expression yields a Base64 item.
   * Returns the item or throws an exception.
   * @param empty allow empty result
   * @param ex expression to be evaluated
   * @param qc query context
   * @return Bas64 item
   * @throws QueryException query exception
   */
  protected final B64 toB64(final Expr ex, final QueryContext qc, final boolean empty)
      throws QueryException {
    return toB64(ex.atomItem(qc, info), empty);
  }

  /**
   * Checks if the specified item is a Base64 item.
   * @param empty allow empty result
   * @param it item
   * @return Bas64 item, or {@code null} if the item is an empty sequences and {@code empty} is true
   * @throws QueryException query exception
   */
  protected final B64 toB64(final Item it, final boolean empty) throws QueryException {
    if(empty && it == null) return null;
    return (B64) checkType(it, AtomType.B64);
  }

  /**
   * Checks if the specified item is a string or binary item.
   * @param it item to be checked
   * @return byte array
   * @throws QueryException query exception
   */
  protected final byte[] toBytes(final Item it) throws QueryException {
    if(checkNoEmpty(it).type.isStringOrUntyped()) return it.string(info);
    if(it instanceof Bin) return ((Bin) it).binary(info);
    throw STRBIN_X_X.get(info, it.type, it);
  }

  /**
   * Checks if the specified expression yields a QName.
   * Returns the item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @param empty allow empty result
   * @return QNm item
   * @throws QueryException query exception
   */
  protected final QNm toQNm(final Expr ex, final QueryContext qc, final boolean empty)
      throws QueryException {
    return toQNm(ex.atomItem(qc, info), empty);
  }

  /**
   * Checks if the specified item is a QName.
   * Returns the item or throws an exception.
   * @param it item
   * @param empty allow empty result
   * @return QNm item
   * @throws QueryException query exception
   */
  protected final QNm toQNm(final Item it, final boolean empty) throws QueryException {
    if(empty && it == null) return null;
    final Type ip = checkNoEmpty(it, AtomType.QNM).type;
    if(ip == AtomType.QNM) return (QNm) it;
    if(ip.isUntyped()) throw NSSENS_X_X.get(info, ip, AtomType.QNM);
    throw castError(it, AtomType.QNM, info);
  }

  /**
   * Checks if the specified expression yields a function.
   * Returns the function or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @return function item
   * @throws QueryException query exception
   */
  protected FItem toFunc(final Expr ex, final QueryContext qc) throws QueryException {
    return (FItem) checkType(toItem(ex, qc, SeqType.ANY_FUN), SeqType.ANY_FUN);
  }

  /**
   * Checks if the specified expression yields a map.
   * Returns the map or throws an exception.
   * @param ex expression
   * @param qc query context
   * @return map
   * @throws QueryException query exception
   */
  protected Map toMap(final Expr ex, final QueryContext qc) throws QueryException {
    return toMap(toItem(ex, qc, SeqType.ANY_MAP));
  }

  /**
   * Checks if the specified item is a map.
   * Returns the map or throws an exception.
   * @param it item to check
   * @return the map
   * @throws QueryException if the item is not a map
   */
  protected Map toMap(final Item it) throws QueryException {
    if(it instanceof Map) return (Map) it;
    throw castError(it, SeqType.ANY_MAP, info);
  }

  /**
   * Checks if the specified expression yields an array.
   * @param e expression
   * @param qc query context
   * @return array
   * @throws QueryException query exception
   */
  protected Array toArray(final Expr e, final QueryContext qc) throws QueryException {
    return toArray(toItem(e, qc, SeqType.ANY_ARRAY));
  }

  /**
   * Assures that the specified item item is an array.
   * @param it item to check
   * @return the array
   * @throws QueryException if the item is not an array
   */
  protected Array toArray(final Item it) throws QueryException {
    if(it instanceof Array) return (Array) it;
    throw castError(it, SeqType.ANY_ARRAY, info);
  }

  /**
   * Checks if the specified expression yields an item of the specified atomic type.
   * Returns the item or throws an exception.
   * @param ex expression to be evaluated
   * @param qc query context
   * @param type type to be checked
   * @return item
   * @throws QueryException query exception
   */
  protected Item checkAtomic(final Expr ex, final QueryContext qc, final Type type)
      throws QueryException {
    return checkType(ex.atomItem(qc, info), type);
  }

  /**
   * Checks if the specified expression is an empty sequence; if yes, throws
   * an exception.
   * @param it item to be checked
   * @param type type to be checked
   * @return specified item
   * @throws QueryException query exception
   */
  protected Item checkType(final Item it, final Type type) throws QueryException {
    if(checkNoEmpty(it, type).type.instanceOf(type)) return it;
    throw castError(it, type, info);
  }

  /**
   * Checks if the specified item is no empty sequence.
   * @param it item to be checked
   * @return specified item
   * @throws QueryException query exception
   */
  protected final Item checkNoEmpty(final Item it) throws QueryException {
    if(it != null) return it;
    throw EMPTYFOUND.get(info);
  }

  /**
   * Checks if the specified item is no empty sequence.
   * @param it item to be checked
   * @param type expected type
   * @return specified item
   * @throws QueryException query exception
   */
  protected Item checkNoEmpty(final Item it, final Type type) throws QueryException {
    if(it != null) return it;
    throw EMPTYFOUND_X.get(info, type);
  }

  @Override
  protected final FElem planElem(final Object... atts) {
    final int al = atts.length + 4;
    final Object[] tmp = Arrays.copyOf(atts, al);
    tmp[al - 4] = SIZE;
    tmp[al - 3] = size();
    tmp[al - 2] = TYPE;
    tmp[al - 1] = seqType();
    return super.planElem(tmp);
  }
}
