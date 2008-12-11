package org.basex.query.xquery.path;

import static org.basex.query.xquery.XQText.*;
import org.basex.query.xquery.XQContext;
import org.basex.query.xquery.XQException;
import org.basex.query.xquery.expr.Expr;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.item.Nod;
import org.basex.query.xquery.iter.Iter;
import org.basex.query.xquery.iter.NodeIter;
import org.basex.query.xquery.util.Err;

/**
 * Iterative step expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public final class SimpleIterStep extends Step {
  /**
   * Constructor.
   * @param a axis
   * @param t node test
   * @param p predicates
   */
  public SimpleIterStep(final Axis a, final Test t, final Expr[] p) {
    super(a, t, p);
  }
  
  @Override
  public NodeIter iter(final XQContext ctx) throws XQException {
    final Iter iter = checkCtx(ctx);

    // no special predicate treatment?
    return new NodeIter() {
      NodeIter ir;
      
      @Override
      public Nod next() throws XQException {
        while(true) {
          if(ir == null) {
            final Item it = iter.next();
            if(it == null) return null;
            if(!it.node()) Err.or(NODESPATH, this, it.type);
            ir = axis.init((Nod) it);
          }
          final Nod nod = ir.next();
          if(nod == null) ir = null;
          else if(test.e(nod)) return nod.finish();
        }
      }

      @Override
      public String toString() {
        return SimpleIterStep.this.toString();
      }
    };
  }

  /**
   * Check if theres anything to sum up.
   * @return boolean sum up
   */
  public boolean sumUp() {
    return axis == Axis.CHILD && test instanceof NameTest
      && expr.length == 0;
  }
  
}
