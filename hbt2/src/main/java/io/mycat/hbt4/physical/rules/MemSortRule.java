package io.mycat.hbt4.physical.rules;

import io.mycat.hbt4.MycatConvention;
import io.mycat.hbt4.MycatConverterRule;
import io.mycat.hbt4.MycatRules;
import io.mycat.hbt4.physical.MemSort;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Sort;
import org.apache.calcite.tools.RelBuilderFactory;

import java.util.function.Predicate;

public class MemSortRule extends MycatConverterRule {
    public MemSortRule(final MycatConvention out,
                             RelBuilderFactory relBuilderFactory) {
        super(Sort.class, (Predicate<Sort>) project ->
                        true,
                MycatRules.convention, out, relBuilderFactory, "MemSortRule");
    }

    public RelNode convert(RelNode rel) {
        final Sort Sort = (Sort) rel;
        return new MemSort(Sort.getCluster(),Sort.getCluster().traitSetOf(out),Sort.getInput(),Sort.getCollation(),Sort.offset,Sort.fetch);
    }
}