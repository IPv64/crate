/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze.relations;

import io.crate.analyze.AnalyzedStatement;
import io.crate.analyze.AnalyzedStatementVisitor;
import io.crate.exceptions.ColumnUnknownException;
import io.crate.expression.symbol.Symbol;
import io.crate.metadata.ColumnIdent;
import io.crate.metadata.RelationName;
import io.crate.metadata.table.Operation;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a relation
 *
 * <pre>
 *     {@code
 *      tbl
 *      SELECT * FROM tbl
 *
 *      SELECT * FROM tbl AS t
 *
 *      SELECT * FROM tbl1, tbl2
 *
 *      SELECT * FROM tbl AS t1, tbl AS t2
 *
 *      SELECT * FROM (SELECT * FROM tbl) as t
 *     }
 * </pre>
 */
public interface AnalyzedRelation extends AnalyzedStatement {

    <C, R> R accept(AnalyzedRelationVisitor<C, R> visitor, C context);

    Symbol getField(ColumnIdent column, Operation operation) throws UnsupportedOperationException, ColumnUnknownException;

    RelationName relationName();

    @Nonnull
    @Override
    List<Symbol> outputs();

    /**
     * Calls the consumer for each top-level symbol in the relation
     * (Arguments/children of function symbols are not visited)
     */
    @Override
    default void visitSymbols(Consumer<? super Symbol> consumer) {
        for (Symbol output : outputs()) {
            consumer.accept(output);
        }
    }

    @Override
    default <C, R> R accept(AnalyzedStatementVisitor<C, R> visitor, C context) {
        return visitor.visitSelectStatement(this, context);
    }

    @Override
    default boolean isWriteOperation() {
        return false;
    }
}
