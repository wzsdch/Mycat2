package io.mycat.hbt4.physical.rules;


import io.mycat.hbt4.MycatConvention;
import io.mycat.hbt4.MycatConverterRule;
import io.mycat.hbt4.MycatRules;
import io.mycat.hbt4.physical.SortMergeJoin;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.tools.RelBuilderFactory;

import java.util.function.Predicate;

public class SortMergeJoinRule extends MycatConverterRule {
    public SortMergeJoinRule(final MycatConvention out,
                              RelBuilderFactory relBuilderFactory) {
        super(Join.class, (Predicate<Join>) project ->
                        true,
                MycatRules.convention, out, relBuilderFactory, "SortMergeJoinRule");
    }

    public RelNode convert(RelNode rel) {
        final Join join = (Join) rel;
        return new SortMergeJoin(join.getCluster(),
                join.getCluster().traitSetOf(out),
                join.getLeft(),
                join.getRight(),
                join.getCondition(),
                join.getJoinType());
    }
}