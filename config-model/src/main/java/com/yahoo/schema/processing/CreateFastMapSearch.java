// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.document.DataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.model.container.search.QueryProfiles;


/**
 * Adds a "_fieldName_keyvalue" attribute to maps with fast-search enabled.
 *
 * The attribute holds one string per map entry, on the form key + separator + value,
 * so that a key-value pair can be matched with a single lexical lookup.
 *
 * @author johsol
 */
public class CreateFastMapSearch extends Processor {

    /** DEL cannot occur in a map key and is accepted by Text.isTextCharacter. */
    static final String KEY_VALUE_SEPARATOR = String.valueOf((char) 0x7F);

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
            if (!shouldCreateFastMapAttribute(field)) {
                continue;
            }
            SDField keyValueField = createFastMapField(field, keyValueFieldName(field.getName()), validate);
            schema.addExtraField(keyValueField);
            schema.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, keyValueField.getName());
        }
    }

    /** Returns whether a fast map attribute should be created for the given field. */
    private boolean shouldCreateFastMapAttribute(SDField field) {
        return (field.getDataType() instanceof MapDataType) && field.hasFastMapSearch();
    }

    /**
     * Creates a synthetic attribute for a map with fast search. The attribute has data
     * type array of strings since we will do lexical search on the key-value pairs of the map.
     */
    private SDField createFastMapField(SDField inputField, String fieldName, boolean validate) {
        if (validate && (schema.getConcreteField(fieldName) != null || schema.getAttribute(fieldName) != null)) {
            throw newProcessException(schema, null, "Incompatible map attribute '" + fieldName + "' already created.");
        }

        SDField field = new SDField(repo, fieldName, DataType.getArray(DataType.STRING));
        Attribute attribute = new Attribute(fieldName, Attribute.Type.STRING, Attribute.CollectionType.ARRAY);
        attribute.setFastSearch(true);
        field.addAttribute(attribute);
        field.setIndexingScript(schema.getName(), keyValueScript(inputField, fieldName));
        return field;
    }

    /**
     * The synthetic attribute is named _&lt;field&gt;_keyvalue. The leading underscore makes the name
     * impossible to declare in a schema while still being a legal identifier in the indexing language.
     */
    static String keyValueFieldName(String fieldName) {
        return fieldName + "$keyvalue";
    }

    /** Builds "input mapField | for_each { get_field $key . separator . get_field $value } | attribute". */
    private static ScriptExpression keyValueScript(SDField inputField, String fieldName) {
        return new ScriptExpression(
                new StatementExpression(
                        new InputExpression(inputField.getName()),
                        new ForEachExpression(
                                // Note: the array cast picks the varargs constructor. CatExpression is an
                                // Iterable<Expression>, so passing it directly would flatten it into a pipeline.
                                new StatementExpression(new Expression[] {
                                        new CatExpression(
                                                new GetFieldExpression("$key"),
                                                new ConstantExpression(new StringFieldValue(KEY_VALUE_SEPARATOR)),
                                                new GetFieldExpression("$value")) })),
                        new AttributeExpression(fieldName)));
    }

}
