package io.mycat.calcite.plan;

import cn.mycat.vertx.xa.XaSqlConnection;
import io.mycat.*;
import io.mycat.api.collector.RowObservable;
import io.mycat.beans.mycat.MycatRowMetaData;
import io.mycat.calcite.CodeExecuterContext;
import io.mycat.calcite.JdbcConnectionUsage;
import io.mycat.calcite.ProxyConnectionUsage;
import io.mycat.calcite.physical.MycatInsertRel;
import io.mycat.calcite.physical.MycatUpdateRel;
import io.mycat.calcite.resultset.CalciteRowMetaData;
import io.mycat.calcite.spm.Plan;
import io.mycat.util.CalciteConvertors;
import io.mycat.util.VertxUtil;
import io.mycat.vertx.VertxExecuter;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.vertx.core.Future;
import io.vertx.core.impl.future.PromiseInternal;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.EnumerableDefaults;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.runtime.ArrayBindable;
import org.apache.calcite.util.RxBuiltInMethodImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Function;


public class ObservablePlanImplementorImpl implements PlanImplementor {
    private final static Logger LOGGER = LoggerFactory.getLogger(ObservablePlanImplementorImpl.class);
    private XaSqlConnection xaSqlConnection;
    private final MycatDataContext context;
    private final List<Object> params;
    private final Response response;

    public ObservablePlanImplementorImpl(XaSqlConnection xaSqlConnection, MycatDataContext context, List<Object> params, Response response) {
        this.xaSqlConnection = xaSqlConnection;
        this.context = context;
        this.params = params;
        this.response = response;
    }

    @Override
    public PromiseInternal<Void> execute(MycatUpdateRel mycatUpdateRel) {
        PromiseInternal<Void> promise = VertxUtil.newPromise();
        Future<long[]> future = VertxExecuter.runMycatUpdateRel(xaSqlConnection, context, mycatUpdateRel, params);
        future.onComplete(event -> {
            if (event.succeeded()) {
                long[] result = event.result();
                promise.tryComplete();
                if (LOGGER.isDebugEnabled()){
                    LOGGER.info("sendOk "+ Arrays.toString(result));
                }
                response.sendOk(result[0], result[1]);
            } else {
                if (LOGGER.isDebugEnabled()){
                    LOGGER.error("sendError ",event.cause());
                }
                promise.fail(event.cause());
                response.sendError(event.cause());
            }
        });
        return promise;
    }

    @Override
    public PromiseInternal<Void> execute(MycatInsertRel logical) {
        PromiseInternal<Void> promise = VertxUtil.newPromise();
        Future<long[]> future = VertxExecuter.runMycatInsertRel(xaSqlConnection, context, logical, params);
        future.onComplete(event -> {
            if (event.succeeded()) {
                long[] result = event.result();
                promise.tryComplete();
                if (LOGGER.isDebugEnabled()){
                    LOGGER.info("sendOk "+ Arrays.toString(result));
                }
                response.sendOk(result[0], result[1]);
            } else {
                if (LOGGER.isDebugEnabled()){
                    LOGGER.error("sendError ",event.cause());
                }
                promise.fail(event.cause());
                response.sendError(event.cause());
            }
        });
        return promise;
    }

    @Override
    public PromiseInternal<Void> execute(Plan plan) {
        CalciteRowMetaData calciteRowMetaData = new CalciteRowMetaData(plan.getPhysical().getRowType().getFieldList());
        RowObservable rowObservable = new RowObservable() {

            @Override
            protected void subscribeActual(@NonNull Observer<? super Object[]> observer) {
                CodeExecuterContext codeExecuterContext = plan.getCodeExecuterContext();
                ArrayBindable bindable = codeExecuterContext.getBindable();

                ProxyConnectionUsage proxyConnectionUsage = JdbcConnectionUsage.computeProxyTargetConnection(context, params, codeExecuterContext);
                Future<IdentityHashMap<RelNode, List<RowObservable>>> collect = proxyConnectionUsage.collect(xaSqlConnection, params);
                collect.map(relNodeListIdentityHashMap -> {
                       MycatWorkerProcessor processor = MetaClusterCurrent.wrapper(MycatWorkerProcessor.class);
                    processor.getMycatWorker().execute(new Runnable() {
                        @Override
                        public void run() {
                            AsyncMycatDataContextImplImpl newMycatDataContext =
                                    new AsyncMycatDataContextImplImpl(context, codeExecuterContext, (IdentityHashMap) relNodeListIdentityHashMap, params, plan.forUpdate());
                            Object bindObservable;
                            if(codeExecuterContext.getCode().contains("hashJoin(org")){
                                bindObservable = bindObservable(newMycatDataContext);
                            }else {
                                bindObservable = bindable.bindObservable(newMycatDataContext);
                            }
                            if (bindObservable instanceof Observable) {
                                Observable<Object[]> observable = (Observable) bindObservable;
                                observable.subscribe(observer);
                            } else {
                                Enumerable<Object[]> observable = (Enumerable) bindObservable;
                                List<Object[]> objects = observable.toList();
                                Observable<Object[]> observable1 = Observable.fromIterable(objects);
                                observable1.subscribe(observer);
                            }
                        }
                    });

                    return null;
                }).onFailure(event -> {
                    observer.onError(event);
                });
            }

            @Override
            public MycatRowMetaData getRowMetaData() {
                return calciteRowMetaData;
            }
        };
        return response.sendResultSet(rowObservable);
    }
    public Object bindObservable(final org.apache.calcite.runtime.NewMycatDataContext root) {
        final org.apache.calcite.rel.RelNode v1stashed = (org.apache.calcite.rel.RelNode) root.get("v1stashed");
        final org.apache.calcite.rel.RelNode v0stashed = (org.apache.calcite.rel.RelNode) root.get("v0stashed");
        Enumerable<Object[]> objects1 = Linq4j.asEnumerable(RxBuiltInMethodImpl.toEnumerable(
                root.getObservable(v0stashed)).toList());
        Enumerable<Object[]> objects2 = Linq4j.asEnumerable(RxBuiltInMethodImpl.toEnumerable(root.getObservable(v1stashed)).toList());
        final org.apache.calcite.linq4j.Enumerable _inputEnumerable = objects1.hashJoin(
                objects2 ,
                new org.apache.calcite.linq4j.function.Function1() {
                    public java.math.BigDecimal apply(Object[] v1) {
                        return v1[0] == null ? (java.math.BigDecimal) null : org.apache.calcite.runtime.SqlFunctions.toBigDecimal(v1[0]);
                    }
                    public Object apply(Object v1) {
                        return apply(
                                (Object[]) v1);
                    }
                }
                , new org.apache.calcite.linq4j.function.Function1() {
                    public java.math.BigDecimal apply(Object[] v1) {
                        return v1[3] == null ? (java.math.BigDecimal) null : org.apache.calcite.runtime.SqlFunctions.toBigDecimal(v1[3]);
                    }
                    public Object apply(Object v1) {
                        return apply(
                                (Object[]) v1);
                    }
                }
                , new org.apache.calcite.linq4j.function.Function2() {
                    public Object[] apply(Object[] left, Object[] right) {
                        return new Object[] {
                                left[0],
                                left[1],
                                left[2],
                                left[3],
                                left[4],
                                left[5],
                                right == null ? null : right[0],
                                right == null ? null : right[1],
                                right == null ? null : right[2],
                                right == null ? null : right[3]};
                    }
                    public Object[] apply(Object left, Object right) {
                        return apply(
                                (Object[]) left,
                                (Object[]) right);
                    }
                }
                , null, false, true, null);
        final org.apache.calcite.linq4j.AbstractEnumerable left = new org.apache.calcite.linq4j.AbstractEnumerable(){
            public org.apache.calcite.linq4j.Enumerator enumerator() {
                return new org.apache.calcite.linq4j.Enumerator(){
                    public final org.apache.calcite.linq4j.Enumerator inputEnumerator = _inputEnumerable.enumerator();
                    public void reset() {
                        inputEnumerator.reset();
                    }

                    public boolean moveNext() {
                        return inputEnumerator.moveNext();
                    }

                    public void close() {
                        inputEnumerator.close();
                    }

                    public Object current() {
                        final Object[] current = (Object[]) inputEnumerator.current();
                        final Object input_value = current[0];
                        final Object input_value0 = current[1];
                        final Object input_value1 = current[2];
                        final Object input_value2 = current[3];
                        final Object input_value3 = current[4];
                        final Object input_value4 = current[5];
                        final Object input_value5 = current[6];
                        final Object input_value6 = current[7];
                        final Object input_value7 = current[8];
                        return new Object[] {
                                input_value,
                                input_value0,
                                input_value1,
                                input_value2,
                                input_value3,
                                input_value4,
                                input_value5,
                                input_value6,
                                input_value7};
                    }

                };
            }

        };
        final org.apache.calcite.linq4j.Enumerable _inputEnumerable0 = org.apache.calcite.util.RxBuiltInMethodImpl.toEnumerable(left).hashJoin(org.apache.calcite.util.RxBuiltInMethodImpl.toEnumerable(root.getObservable(v1stashed)), new org.apache.calcite.linq4j.function.Function1() {
                    public java.math.BigDecimal apply(Object[] v1) {
                        return v1[0] == null ? (java.math.BigDecimal) null : org.apache.calcite.runtime.SqlFunctions.toBigDecimal(v1[0]);
                    }
                    public Object apply(Object v1) {
                        return apply(
                                (Object[]) v1);
                    }
                }
                , new org.apache.calcite.linq4j.function.Function1() {
                    public java.math.BigDecimal apply(Object[] v1) {
                        return v1[3] == null ? (java.math.BigDecimal) null : org.apache.calcite.runtime.SqlFunctions.toBigDecimal(v1[3]);
                    }
                    public Object apply(Object v1) {
                        return apply(
                                (Object[]) v1);
                    }
                }
                , new org.apache.calcite.linq4j.function.Function2() {
                    public Object[] apply(Object[] left, Object[] right) {
                        return new Object[] {
                                left[0],
                                left[1],
                                left[2],
                                left[3],
                                left[4],
                                left[5],
                                left[6],
                                left[7],
                                left[8],
                                right == null ? null : right[0],
                                right == null ? null : right[1],
                                right == null ? null : right[2],
                                right == null ? null : right[3]};
                    }
                    public Object[] apply(Object left, Object right) {
                        return apply(
                                (Object[]) left,
                                (Object[]) right);
                    }
                }
                , null, false, true, null);
        final org.apache.calcite.linq4j.AbstractEnumerable child = new org.apache.calcite.linq4j.AbstractEnumerable(){
            public org.apache.calcite.linq4j.Enumerator enumerator() {
                return new org.apache.calcite.linq4j.Enumerator(){
                    public final org.apache.calcite.linq4j.Enumerator inputEnumerator = _inputEnumerable0.enumerator();
                    public void reset() {
                        inputEnumerator.reset();
                    }

                    public boolean moveNext() {
                        return inputEnumerator.moveNext();
                    }

                    public void close() {
                        inputEnumerator.close();
                    }

                    public Object current() {
                        final Object[] current = (Object[]) inputEnumerator.current();
                        final Object input_value = current[0];
                        final Object input_value0 = current[1];
                        final Object input_value1 = current[2];
                        final Object input_value2 = current[3];
                        final Object input_value3 = current[4];
                        final Object input_value4 = current[5];
                        final Object input_value5 = current[6];
                        final Object input_value6 = current[7];
                        final Object input_value7 = current[8];
                        final Object input_value8 = current[9];
                        final Object input_value9 = current[10];
                        final Object input_value10 = current[11];
                        return new Object[] {
                                input_value,
                                input_value0,
                                input_value1,
                                input_value2,
                                input_value3,
                                input_value4,
                                input_value5,
                                input_value6,
                                input_value7,
                                input_value8,
                                input_value9,
                                input_value10};
                    }

                };
            }

        };
        return org.apache.calcite.linq4j.EnumerableDefaults.orderBy((Enumerable) org.apache.calcite.util.RxBuiltInMethodImpl.toEnumerable(child), new org.apache.calcite.linq4j.function.Function1() {
                    public java.math.BigDecimal apply(Object[] v) {
                        return v[0] == null ? (java.math.BigDecimal) null : org.apache.calcite.runtime.SqlFunctions.toBigDecimal(v[0]);
                    }
                    public Object apply(Object v) {
                        return apply(
                                (Object[]) v);
                    }
                }
                , (Comparator)org.apache.calcite.linq4j.function.Functions.nullsComparator(false, false), 0, 2147483647);
    }

}