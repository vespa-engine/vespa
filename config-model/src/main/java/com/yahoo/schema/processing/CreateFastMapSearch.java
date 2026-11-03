// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.Case;
import com.yahoo.schema.document.Dictionary;
import com.yahoo.schema.document.FastMapSearchFields;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.searchlib.document.FastMapSearch;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExcessHex16DoubleEncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExcessHex16EncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExcessHex8EncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExcessHex8FloatEncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ParenthesisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToStringExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;


/**
 * Adds a "fieldName$lookupName" attribute to maps, and arrays of struct used as maps, for each 'fast-search-map-field'.
 *
 * The attribute holds one string per map entry or array element, on the form key + separator + value,
 * so that a key-value pair can be matched with a single lexical lookup.
 *
 * @author johsol
 */
public class CreateFastMapSearch extends Processor {

    private final SDDocumentType repo;

    public CreateFastMapSearch(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
        repo = schema.getDocument();
    }

    @Override
    public void process(boolean validate, boolean documentsOnly, ModelContext.Properties properties) {
        process(validate, documentsOnly);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (documentsOnly) {
            return;
        }

        for (SDField field : schema.allConcreteFields()) {
            for (FastMapSearchFields fastMapFields : field.getFastMapSearches()) {
                if (!shouldCreateFastMapAttribute(fastMapFields)) {
                    continue;
                }

                String fieldName = FastMapSearch.toLookupFieldName(field.getName(), fastMapFields.lookupName());
                // Inheritance: there is a parent that has already made the attribute.
                var existing = schema.getConcreteField(fieldName);
                if (existing != null && existing.isInternalField()) {
                    continue;
                }

                SDField keyValueField = createFastMapField(field, fastMapFields, fieldName, validate);
                schema.addExtraField(keyValueField);
                schema.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, keyValueField.getName());
            }
        }
    }

    /** Returns whether a fast map attribute should be created for the given fast map search. */
    private boolean shouldCreateFastMapAttribute(FastMapSearchFields fastMapFields) {
        return fastMapFields.isMap() || fastMapFields.isArrayOfStruct();
    }

    /**
     * Creates a synthetic attribute for a map, or an array of struct, with fast search. The attribute has data
     * type array of strings since we will do lexical search on the key-value pairs of the map.
     */
    private SDField createFastMapField(SDField inputField, FastMapSearchFields fastMapFields, String fieldName, boolean validate) {
        if (validate && (schema.getConcreteField(fieldName) != null || schema.getAttribute(fieldName) != null)) {
            throw newProcessException(schema.getName(), inputField.getName(),
                                      "Incompatible map attribute '" + fieldName + "' already created.");
        }

        SDField field = new SDField(repo, fieldName, DataType.getArray(DataType.STRING));
        Attribute attribute = new Attribute(fieldName, Attribute.Type.STRING, Attribute.CollectionType.ARRAY);
        attribute.setFastSearch(true);
        field.addAttribute(attribute);
        if (isCasedKeyValue(inputField, fastMapFields, validate)) {
            // Uncased is the default, so only a cased map has anything to set. The field must carry the
            // casing too, as later processors derive attribute casing from it, and the dictionary must be
            // cased or the lookup would be folded.
            field.setMatchingCase(Case.CASED);
            attribute.setCase(Case.CASED);
            Dictionary dictionary = field.getOrSetDictionary();
            dictionary.updateMatch(Case.CASED);
            attribute.setDictionary(dictionary);
        }
        field.setIndexingScript(schema.getName(), keyValueScript(fastMapFields, inputField.getName(), fieldName));
        field.setInternalField(true);
        return field;
    }

    /**
     * Returns whether the synthetic attribute of the given map field is matched cased. Key and value share one
     * term, so a string value must be matched like a string key. A numeric key is in decimal, and a numeric value
     * hex encoded, so they are matched the same way whichever casing the term has. With a numeric key, the attribute
     * therefore follows the casing of a string value. Casing on a numeric key is ignored, with a warning,
     * as for any non-string field.
     */
    private boolean isCasedKeyValue(SDField inputField, FastMapSearchFields fastMapFields, boolean validate) {
        boolean stringValue = fastMapFields.valueType() == DataType.STRING;
        if (fastMapFields.keyType() != DataType.STRING) {
            return stringValue && isCased(inputField, fastMapFields.valueField());
        }
        boolean casedKey = isCased(inputField, fastMapFields.keyField());
        if ( ! stringValue) {
            return casedKey;
        }
        boolean casedValue = isCased(inputField, fastMapFields.valueField());
        if (validate && casedKey != casedValue) {
            throw newProcessException(schema.getName(), inputField.getName(),
                                      "Map with fast search requires the same match casing on the key and the value, " +
                                      "but only the " + (casedKey ? "key" : "value") + " is cased.");
        }
        return casedKey;
    }

    /** Returns whether the named struct field of the given map or array of struct field is matched cased. */
    private static boolean isCased(SDField mapField, String structFieldName) {
        SDField structField = mapField.getStructField(structFieldName);
        return structField != null && structField.getMatching().getCase() == Case.CASED;
    }

    /**
     * Builds "input mapField | for_each { KEY_EXP . separator . VALUE_EXP } | attribute",
     * where KEY and VALUE are $key and $value for a map, and the key and value struct fields for an array of struct,
     * KEY_EXP is "(get_field KEY | to_string)" if the key type is int or long, and "get_field KEY" otherwise,
     * and VALUE_EXP is
     * "(get_field VALUE | exhex8encode)" if the value type is int,
     * "(get_field VALUE | exhex16encode)" if the value type is long,
     * "(get_field VALUE | exhex8floatencode)" if the value type is float,
     * "(get_field VALUE | exhex16doubleencode)" if the value type is double,
     * "get_field VALUE" if the value type is string,
     * and throws an IllegalArgumentException otherwise.
     * */
    private static ScriptExpression keyValueScript(FastMapSearchFields fastMapFields, String inputFieldName, String fieldName) {
        String keyName = fastMapFields.isMap() ? "$key" : fastMapFields.keyField();
        String valueName = fastMapFields.isMap() ? "$value" : fastMapFields.valueField();
        return new ScriptExpression(
                new StatementExpression(
                        new InputExpression(inputFieldName),
                        new ForEachExpression(
                                // Note: the array cast picks the varargs constructor. CatExpression is an
                                // Iterable<Expression>, so passing it directly would flatten it into a pipeline.
                                new StatementExpression(new Expression[] {
                                        new CatExpression(
                                                keyExpression(keyName, fastMapFields.keyType()),
                                                new ConstantExpression(new StringFieldValue(FastMapSearch.keyValueSeparator())),
                                                valueExpression(valueName, fastMapFields.valueType())) })),
                        new AttributeExpression(fieldName)));
    }

    /**
     * Builds "(get_field KEY | to_string)" if the key type is int or long, giving the decimal form of the key,
     * and "get_field KEY" otherwise.
     */
    private static Expression keyExpression(String keyName, DataType keyType) {
        var field = new GetFieldExpression(keyName);
        if (keyType == DataType.INT || keyType == DataType.LONG) {
            return new ParenthesisExpression(new StatementExpression(field, new ToStringExpression()));
        }
        return field;
    }

    /**
     * Builds
     * "(get_field VALUE | exhex8encode)" if the value type is int,
     * "(get_field VALUE | exhex16encode)" if the value type is long,
     * "(get_field VALUE | exhex8floatencode)" if the value type is float,
     * "(get_field VALUE | exhex16doubleencode)" if the value type is double,
     * "get_field VALUE" if the value type is string,
     * and throws an IllegalArgumentException otherwise.
     * */
    private static Expression valueExpression(String valueName, DataType valueType) {
        var field = new GetFieldExpression(valueName);

        if (valueType == DataType.INT) {
            return new ParenthesisExpression(new StatementExpression(field, new ExcessHex8EncodeExpression()));
        } else if (valueType == DataType.LONG) {
            return new ParenthesisExpression(new StatementExpression(field, new ExcessHex16EncodeExpression()));
        } else if (valueType == DataType.FLOAT) {
            return new ParenthesisExpression(new StatementExpression(field, new ExcessHex8FloatEncodeExpression()));
        } else if (valueType == DataType.DOUBLE) {
            return new ParenthesisExpression(new StatementExpression(field, new ExcessHex16DoubleEncodeExpression()));
        } else if (valueType == DataType.STRING) {
            return field;
        } else {
            throw new IllegalArgumentException("Value type '" + valueType + "' is not supported.");
        }
    }

}
