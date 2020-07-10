package io.mycat.hbt4.logical;

import com.google.common.collect.ImmutableList;
import io.mycat.hbt4.Executor;
import io.mycat.hbt4.ExecutorImplementor;
import io.mycat.hbt4.ExplainWriter;
import io.mycat.hbt4.MycatRel;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Values;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexLiteral;

import java.util.List;

/**
 * Values operator implemented in Mycat convention.
 */
public class MycatValues extends Values implements MycatRel {
  public   MycatValues(RelOptCluster cluster, RelDataType rowType,
                ImmutableList<ImmutableList<RexLiteral>> tuples, RelTraitSet traitSet) {
        super(cluster, rowType, tuples, traitSet);
    }

    @Override
    public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
        assert inputs.isEmpty();
        return new MycatValues(getCluster(), rowType, tuples, traitSet);
    }


    @Override
    public ExplainWriter explain(ExplainWriter writer) {
        return writer.name("MycatValues").item("values", tuples).ret();
    }

    @Override
    public Executor implement(ExecutorImplementor implementor) {
        return implementor.implement(this);
    }
}