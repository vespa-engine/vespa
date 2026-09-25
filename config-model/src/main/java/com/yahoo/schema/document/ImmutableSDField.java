// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    boolean doesBitPacking();

    boolean doesLowerCasing();

    boolean isExtraField();

    boolean isImportedField();

    boolean isIndexStructureField();

    boolean usesStructOrMap();

    boolean hasFastMapSearch();

    /** Returns the key and value fields if this has fast map search, and null otherwise. */
    FastMapSearchFields getFastMapSearch();

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

    String getIndexLinguisticsProfile();

    String getSearchLinguisticsProfile();

    TokensMode getIndexLinguisticsTokens();

    TokensMode getSearchLinguisticsTokens();

    ImmutableSDField getStructField(String name);

    Collection<? extends ImmutableSDField> getStructFields();

    Stemming getStemming();

    Stemming getStemming(Schema schema);

    /**
     * Returns the stemming to use when turning the content of this field into tokens:
     * The index side of the linguistics tokens setting of this field if set, otherwise the
     * stemming setting of this field, or of the schema if this field has none. Never null.
     * Not supported for imported fields, which are attributes and are never stemmed.
     */
    default Stemming getIndexStemming(Schema schema) {
        TokensMode tokens = getIndexLinguisticsTokens();
        return tokens != null ? tokens.toStemming() : getStemming(schema);
    }

    /**
     * Returns the stemming to use when turning query terms for this field into tokens:
     * The search side of the linguistics tokens setting of this field if set, otherwise the
     * stemming setting of this field, or of the schema if this field has none. Never null.
     * Not supported for imported fields, which are attributes and are never stemmed.
     */
    default Stemming getSearchStemming(Schema schema) {
        TokensMode tokens = getSearchLinguisticsTokens();
        return tokens != null ? tokens.toStemming() : getStemming(schema);
    }

    /**
     * Returns the stemming which is in effect when searching this field, taking every setting which
     * can influence it into account. This is what the index-info derived config tells the query side
     * to do, so every check of the query side stemming of a field must resolve it through this:
     * <ol>
     *   <li>The search side of the linguistics tokens setting of this field, if set.</li>
     *   <li>Otherwise, the stemming setting of this field, or of the schema if this field has none,
     *       when that is {@link Stemming#NONE}: An index level setting can change how a field is
     *       stemmed, but cannot turn stemming on for a field which has it off. Word and exact
     *       matching rely on this, as they turn stemming off by setting it to NONE on the field.</li>
     *   <li>Otherwise, the stemming setting of the index of this field, if any: given in an index
     *       block in this field, or in a schema level index block of the same name.</li>
     *   <li>Otherwise, the stemming setting of this field, or of the schema.</li>
     * </ol>
     * Never null. Not supported for imported fields, which are attributes and are never stemmed.
     */
    default Stemming getEffectiveSearchStemming(Schema schema) {
        TokensMode tokens = getSearchLinguisticsTokens();
        if (tokens != null) return tokens.toStemming();
        Stemming stemming = getStemming(schema);
        if (stemming == Stemming.NONE) return stemming;
        Stemming indexStemming = getStemmingOfIndex(schema);
        return indexStemming != null ? indexStemming : stemming;
    }

    /**
     * Returns the stemming setting of the index of this field, given in an index block in this field,
     * or otherwise in a schema level index block of the same name, or null if neither sets one.
     * This is not consulted through {@link Schema#getIndex}, as that loses the stemming setting
     * when consolidating a field level and a schema level block of the same name.
     */
    default Stemming getStemmingOfIndex(Schema schema) {
        Index fieldIndex = getIndex(getName());
        if (fieldIndex != null && fieldIndex.getStemming() != null) return fieldIndex.getStemming();
        return schema.getSchemaIndex(getName()).map(Index::getStemming).orElse(null);
    }

    /**
     * Returns whether stemming, and thereby the linguistics tokens setting, applies to this field at all:
     * It must be a string field with text matching, and not an imported one. Every place which decides
     * what the query side should do with the terms of a field must skip the fields where this is false,
     * and must do so before resolving stemming: an imported field does not support that at all, and a
     * field which cannot be stemmed must not be allowed to decide the stemming of a fieldset it is in.
     */
    default boolean isStemmable() {
        if (isImportedField()) return false; // imported fields are attributes, which are not stemmed
        // Word, exact and gram matching produce a single token, so such a field is never stemmed
        if (getMatching().getType() != MatchType.TEXT) return false;
        return isOfTypeOrNested(DataType.STRING);
    }

    /** Returns whether this field is of the given type, or is a collection of it. */
    default boolean isOfTypeOrNested(DataType type) {
        return type.equals(getDataType()) || type.equals(getDataType().getNestedType());
    }

    Ranking getRanking();

    String getName();

    Map<String, SummaryField> getSummaryFields();

    /** Returns a {@link Field} representation (which is sadly not immutable) */
    Field asField();

    /** Returns true if this is a document field (not a synthetic field), or a mutable attribute. */
    boolean hasFullIndexingDocprocRights();

    int getWeight();
    int getLiteralBoost();
    RankType getRankType();
    Map<String, Index> getIndices();
    boolean existsIndex(String name);
    SummaryField getSummaryField(String name);
    boolean hasIndex();

}
