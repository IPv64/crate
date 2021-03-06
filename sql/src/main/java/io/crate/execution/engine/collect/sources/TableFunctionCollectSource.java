/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.execution.engine.collect.sources;

import com.google.common.collect.Iterables;
import io.crate.data.BatchIterator;
import io.crate.data.InMemoryBatchIterator;
import io.crate.data.Input;
import io.crate.data.Row;
import io.crate.data.SentinelRow;
import io.crate.execution.dsl.phases.CollectPhase;
import io.crate.execution.dsl.phases.TableFunctionCollectPhase;
import io.crate.execution.engine.collect.CollectTask;
import io.crate.execution.engine.collect.InputCollectExpression;
import io.crate.execution.engine.collect.ValueAndInputRow;
import io.crate.expression.InputCondition;
import io.crate.expression.InputFactory;
import io.crate.expression.symbol.Symbol;
import io.crate.metadata.Functions;
import io.crate.metadata.TransactionContext;
import io.crate.metadata.tablefunctions.TableFunctionImplementation;
import io.crate.types.RowType;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class TableFunctionCollectSource implements CollectSource {

    private final InputFactory inputFactory;

    @Inject
    public TableFunctionCollectSource(Functions functions) {
        inputFactory = new InputFactory(functions);
    }

    @Override
    public BatchIterator<Row> getIterator(TransactionContext txnCtx,
                                          CollectPhase collectPhase,
                                          CollectTask collectTask,
                                          boolean supportMoveToStart) {
        TableFunctionCollectPhase phase = (TableFunctionCollectPhase) collectPhase;
        TableFunctionImplementation<?> functionImplementation = phase.functionImplementation();
        RowType rowType = functionImplementation.returnType();

        //noinspection unchecked  Only literals can be passed to table functions. Anything else is invalid SQL
        List<Input<?>> inputs = (List<Input<?>>) (List<?>) phase.functionArguments();

        List<Input<?>> topLevelInputs = new ArrayList<>(phase.toCollect().size());
        List<String> columns = rowType.fieldNames();
        InputFactory.Context<InputCollectExpression> ctx = inputFactory.ctxForRefs(
            txnCtx,
            ref -> {
                for (int i = 0; i < columns.size(); i++) {
                    String column = columns.get(i);
                    if (ref.column().isTopLevel() && ref.column().name().equals(column)) {
                        return new InputCollectExpression(i);
                    }
                }
                throw new IllegalStateException("Column `" + ref + "` not found in " + functionImplementation.info().ident());
            });
        for (Symbol symbol : phase.toCollect()) {
            topLevelInputs.add(ctx.add(symbol));
        }

        Iterable<Row> result = functionImplementation.evaluate(txnCtx, inputs.toArray(new Input[0]));
        Iterable<Row> rows = Iterables.transform(
            result,
            new ValueAndInputRow<>(topLevelInputs, ctx.expressions()));
        Input<Boolean> condition = (Input<Boolean>) ctx.add(phase.where());
        rows = Iterables.filter(rows, InputCondition.asPredicate(condition));
        return InMemoryBatchIterator.of(rows, SentinelRow.SENTINEL, functionImplementation.hasLazyResultSet());
    }
}
