package org.basex.query.func.hof;

import static org.basex.query.QueryError.*;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.iter.*;
import org.basex.query.value.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Leo Woerteler
 */
public final class HofFoldLeft1 extends StandardFunc {
  @Override
  public Iter iter(final QueryContext qc) throws QueryException {
    return value(qc).iter();
  }

  @Override
  public Value value(final QueryContext qc) throws QueryException {
    final FItem f = checkArity(exprs[1], 2, qc);
    final Iter iter = qc.iter(exprs[0]);
    Value sum = checkNoEmpty(iter.next());
    for(Item it; (it = iter.next()) != null;) {
      qc.checkStop();
      sum = f.invokeValue(qc, info, sum, it);
    }
    return sum;
  }

  @Override
  protected Expr opt(final CompileContext cc) throws QueryException {
    final Expr ex1 = exprs[0], ex2 = exprs[1];
    if(ex1 == Empty.SEQ) throw EMPTYFOUND.get(info);
    if(allAreValues() && ex1.size() <= UNROLL_LIMIT) {
      final Value seq = (Value) ex1;
      final FItem f = checkArity(ex2, 2, cc.qc);
      Expr ex = seq.itemAt(0);
      final long is = seq.size();
      for(int i = 1; i < is; i++) {
        ex = new DynFuncCall(info, sc, f, ex, seq.itemAt(i)).optimize(cc);
      }
      cc.info(QueryText.OPTUNROLL_X, this);
      return ex;
    }

    final Type t = ex2.seqType().type;
    if(t instanceof FuncType) exprType.assign(((FuncType) t).declType);
    return this;
  }
}
