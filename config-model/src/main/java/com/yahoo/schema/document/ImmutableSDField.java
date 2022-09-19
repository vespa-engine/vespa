// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * An interface containing the non-mutating methods of {@link SDField}.
 * For description of the methods see {@link SDField}.
 *
 * @author bjorncs
 */
public interface ImmutableSDField {

    <T extends Expression> boolean containsExpression(Class<T> searchFor);

    boolean doesAttributing();

    boolean doesIndexing();

    boolean doesLowerCasing();

    boolean isExtraField();

    boolean isImportedField();

    boolean isIndexStructureField();

    boolean usesStructOrMap();

    /**
     * Whether this field at some time was configured to do attributing.
     *
     * This function can typically return a different value than doesAttributing(),
     * which uses the final state of the underlying indexing script instead.
     */
    boolean wasConfiguredToDoAttributing();

    /**
     * Whether this field at some time was configured to do indexing.
     *
     * This function can typically return a different value than doesIndexing(),
     * which uses the final state of the underlying indexing script instead.
     */
    boolean wasConfiguredToDoIndexing();

    /**
     * Returns whether this field has a single attribute with the same name as this field.
     */
    boolean hasSingleAttribute();

    DataType getDataType();

    Index getIndex(String name);

    List<String> getQueryCommands();

    Map<String, Attribute> getAttributes();

    Attribute getAttribute();

    Map<String, String> getAliasToName();

    ScriptExpression getIndexingScript();

    Matching getMatching();

    NormalizeLevel getNormalizing();

    ImmutableSDField getStructField(String name);

    Collection<? extends ImmutableSDField> getStructFields();

    Stemming getStemming();

    Stemming getStemming(Schema schema);

    Ranking getRanking();

    String getName();

    Map<String, SummaryField> getSummaryFields();

    /** Returns a {@link Field} representation (which is sadly not immutable) */
    Field asField();

    boolean hasFullIndexingDocprocRights();
    int getWeight();
    int getLiteralBoost();
    RankType getRankType();
    Map<String, Index> getIndices();
    boolean existsIndex(String name);
    SummaryField getSummaryField(String name);
    boolean hasIndex();
}
