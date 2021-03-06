package org.basex.query;

import static org.basex.query.QueryText.*;

import java.util.*;

import org.basex.data.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.func.fn.*;
import org.basex.query.scope.*;
import org.basex.query.value.*;
import org.basex.query.value.array.Array;
import org.basex.query.value.item.*;
import org.basex.query.value.map.Map;
import org.basex.query.value.node.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.query.var.*;
import org.basex.util.*;
import org.basex.util.hash.*;
import org.basex.util.list.*;

/**
 * Compilation context.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public final class CompileContext {
  /** Limit for the size of sequences that are pre-evaluated. */
  public static final int MAX_PREEVAL = 1 << 18;

  /** Query context. */
  public final QueryContext qc;
  /** Variable scope list. */
  private final ArrayList<VarScope> scopes = new ArrayList<>();
  /** Query focus list. */
  private final ArrayList<QueryFocus> focuses = new ArrayList<>();

  /**
   * Constructor.
   * @param qc query context
   */
  public CompileContext(final QueryContext qc) {
    this.qc = qc;
  }

  /**
   * Adds some compilation info.
   * @param string evaluation info
   * @param ext text text extensions
   */
  public void info(final String string, final Object... ext) {
    final TokenList list = new TokenList();
    for(final Object e : ext) list.add(QueryError.chop(TokenBuilder.token(e), null));
    qc.info.compInfo(string, list.toArray());
  }

  /**
   * Pushes a new variable scope to the stack.
   * @param scp variable scope
   */
  public void pushScope(final VarScope scp) {
    scopes.add(scp);
  }

  /**
   * Removes a variable scope from the stack.
   * @return the removed element
   */
  public VarScope removeScope() {
    return scopes.remove(scopes.size() - 1);
  }

  /**
   * Prepares the variable scope for being compiled.
   * This method should be run after compiling a scope.
   * @param scope scope
   */
  public void removeScope(final Scope scope) {
    removeScope().cleanUp(scope);
  }

  /**
   * Caches the current query focus and sets a new focus.
   * @param item context item
   */
  public void pushFocus(final Item item) {
    focuses.add(qc.focus);
    qc.focus = new QueryFocus();
    qc.focus.value = item;
  }

  /**
   * Sets the last focus from the stack.
   */
  public void popFocus() {
    qc.focus = focuses.remove(focuses.size() - 1);
  }

  /**
   * Indicates if any query focus instanced are stored on the stack.
   * @return result of check
   */
  public boolean topFocus() {
    return focuses.isEmpty();
  }

  /**
   * Returns the current variable scope.
   * @return variable scope
   */
  public VarScope vs() {
    return scopes.get(scopes.size() - 1);
  }

  /**
   * Returns the current static context.
   * @return static context
   */
  public StaticContext sc() {
    return vs().sc;
  }

  /**
   * Creates a new copy of the given variable in this scope.
   * @param var variable to copy (can be {@code null})
   * @param vm variable mapping (can be {@code null})
   * @return new variable, or {@code null} if the supplied variable is {@code null}
   */
  public Var copy(final Var var, final IntObjMap<Var> vm) {
    if(var == null) return null;
    final VarScope vs = vs();
    final Var v = vs.add(new Var(var, qc, vs.sc));
    if(vm != null) vm.put(var.id, v);
    return v;
  }

  /**
   * Pre-evaluates the specified expression.
   * @param expr expression
   * @return optimized expression
   * @throws QueryException query exception
   */
  public Expr preEval(final Expr expr) throws QueryException {
    return replaceWith(expr, qc.value(expr));
  }

  /**
   * Adds an optimization info for pre-evaluating the specified expression to an empty sequence.
   * @param result resulting expression
   * @return optimized expression
   */
  public Expr emptySeq(final Expr result) {
    return replaceWith(result, null);
  }

  /**
   * Replaces an EBV expression.
   * @param expr expression
   * @param result resulting expression ({@code null} indicates empty sequence)
   * @return optimized expression
   */
  public Expr replaceEbv(final Expr expr, final Expr result) {
    return replaceWith(expr, result, false);
  }

  /**
   * Replaces an expression with the specified one.
   * @param expr expression
   * @param result resulting expression ({@code null} indicates empty sequence)
   * @return optimized expression
   */
  public Expr replaceWith(final Expr expr, final Expr result) {
    return replaceWith(expr, result, true);
  }

  /**
   * Replaces an expression with the specified one.
   * @param expr expression
   * @param result resulting expression ({@code null} indicates empty sequence)
   * @param refine refine type
   * @return optimized expression
   */
  private Expr replaceWith(final Expr expr, final Expr result, final boolean refine) {
    final Expr res = result == null ? Empty.SEQ : result;
    if(res != expr) {
      final byte[] exprString = QueryError.chop(Token.token(expr.toString()), null);
      final byte[] resString = QueryError.chop(Token.token(res.toString()), null);
      if(!Token.eq(exprString, resString)) {
        final TokenBuilder tb = new TokenBuilder(res instanceof ParseExpr ? OPTREWRITE : OPTPRE);
        tb.add(' ').add(expr.description()).add(" to ").add(res.description()).add(": ");
        info(tb.add(exprString).add(" -> ").add(resString).toString());
      }

      if(res instanceof ParseExpr) {
        // refine type. required mostly for {@link Filter} rewritings
        if(refine) {
          final ParseExpr re = (ParseExpr) res;
          final SeqType et = expr.seqType(), rt = re.seqType();
          if(!et.eq(rt) && et.instanceOf(rt)) {
            final SeqType st = et.intersect(rt);
            if(st != null) re.exprType.assign(st);
          }
        }
      } else if(res != Empty.SEQ && refine) {
        // refine type. required because original type might have got lost in new sequence
        if(res instanceof Seq) {
          final Seq seq = (Seq) res;
          final Type et = expr.seqType().type, rt = seq.type;
          if(!et.eq(rt) && et.instanceOf(rt)) {
            final Type t = et.intersect(rt);
            if(t != null) {
              seq.type = t;
              // Indicate that types may not be homogeneous
              seq.homo = false;
            }
          }
        } else if(res instanceof FItem) {
          // refine type of function items (includes maps and arrays)
          final FItem fitem = (FItem) res;
          final SeqType et = expr.seqType(), rt = res.seqType();
          if(!et.eq(rt) && et.instanceOf(rt)) {
            final Type t = et.type.intersect(rt.type);
            if(t != null) fitem.type = t;
          }
        }
      }
    }
    return res;
  }

  /**
   * Creates an error function instance.
   * @param qe exception to be raised
   * @param expr expression
   * @return function
   */
  public StandardFunc error(final QueryException qe, final Expr expr) {
    return FnError.get(qe, expr.seqType(), sc());
  }

  /**
   * Creates and returns an optimized instance of the specified function.
   * @param func function
   * @param info input info
   * @param exprs expressions
   * @return function
   * @throws QueryException query exception
   */
  public Expr function(final Function func, final InputInfo info, final Expr... exprs)
      throws QueryException {
    return func.get(sc(), info, exprs).optimize(this);
  }

  /**
   * Returns a context value for the given expression.
   * @param expr expression (can be {@code null})
   * @return root value or {@code null}
   */
  public Value contextValue(final Expr expr) {
    // current context value
    final Value v = qc.focus.value;
    // no root or context expression: return context
    if(expr == null || expr instanceof ContextValue) return v;
    // root reference
    if(expr instanceof Root) return v instanceof ANode ? ((ANode) v).root() : v;
    // root is value: return root
    if(expr instanceof Value) return (Value) expr;
    // otherwise, return dummy node
    return dummy(expr);
  }

  /**
   * Returns a context item for the given root.
   * @param root root expression (can be {@code null})
   * @return root item or {@code null}
   */
  public Item contextItem(final Expr root) {
    return dummy(contextValue(root));
  }

  /**
   * Returns a dummy item for the specified expression.
   * @param expr expression
   * @return dummy item or {@code null}
   */
  private static Item dummy(final Expr expr) {
    if(expr != null) {
      final Type type = expr.seqType().type;
      if(expr instanceof Value && expr != Empty.SEQ) {
        // expression is value, type of first item is identical to value type: return this item
        final Item it = ((Value) expr).itemAt(0);
        if(type == it.type) return it;
      }

      // data reference exists: create dummy node
      final Data d = expr.data();
      if(d != null) return new DBNode(d, 0, Data.ELEM);

      // otherwise, return dummy item
      if(type == AtomType.STR) return Str.ZERO;
      if(type == AtomType.ITR) return Int.ZERO;
      if(type == AtomType.DBL) return Dbl.ZERO;
      if(type == AtomType.FLT) return Flt.ZERO;
      if(type == AtomType.DEC) return Dec.ZERO;
      if(type == AtomType.BLN) return Bln.FALSE;
      if(type == NodeType.DOC) return FDoc.DUMMY;
      if(type.instanceOf(SeqType.ANY_MAP)) return Map.EMPTY;
      if(type.instanceOf(SeqType.ANY_ARRAY)) return Array.empty();
      if(type instanceof NodeType) return FElem.DUMMY;
    }
    // otherwise, return null
    return null;
  }

}
