// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.schema.Index;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.vespa.objects.FieldBase;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * Abstract superclass of all search definition processors.
 *
 * @author bratseth
 */
public abstract class Processor {

    protected final Schema schema;
    protected final DeployLogger deployLogger;
    protected final RankProfileRegistry rankProfileRegistry;
    protected final QueryProfiles queryProfiles;

    /**
     * Base constructor
     *
     * @param schema the search to process
     * @param deployLogger Logger du use when logging deploy output.
     * @param rankProfileRegistry Registry with all rank profiles, used for lookup and insertion.
     * @param queryProfiles The query profiles contained in the application this search is part of.
     */
    public Processor(Schema schema,
                     DeployLogger deployLogger,
                     RankProfileRegistry rankProfileRegistry,
                     QueryProfiles queryProfiles) {
        this.schema = schema;
        this.deployLogger = deployLogger;
        this.rankProfileRegistry = rankProfileRegistry;
        this.queryProfiles = queryProfiles;
    }

    /**
     * Processes the input search definition by <b>modifying</b> the input search and its documents, and returns the
     * input search definition.
     *
     * @param validate true to throw exceptions on validation errors, false to make the best possible effort
     *                 at completing processing without throwing an exception.
     *                 If we are not validating, emitting warnings have no effect and can (but must not) be skipped.
     * @param documentsOnly true to skip processing (including validation, regardless of the validate setting)
     *                      of aspects not relating to document definitions (e.g. rank profiles)
     */
    public abstract void process(boolean validate, boolean documentsOnly);

    /** As above, possibly with properties from a context.  Override if needed. */
    public void process(boolean validate, boolean documentsOnly, ModelContext.Properties properties) {
        process(validate, documentsOnly);
    }

    /**
     * Convenience method for adding a no-strings-attached implementation field for a regular field
     *
     * @param schema       the search definition in question
     * @param field        the field to add an implementation field for
     * @param suffix       the suffix of the added implementation field (without the underscore)
     * @param indexing     the indexing statement of the field
     * @param queryCommand the query command of the original field, or null if none
     * @return the implementation field which is added to the search
     */
    protected SDField addField(Schema schema, SDField field, String suffix, String indexing, String queryCommand) {
        SDField implementationField = schema.getConcreteField(field.getName() + "_" + suffix);
        if (implementationField != null) {
            deployLogger.logApplicationPackage(Level.WARNING, "Implementation field " + implementationField + " added twice");
        } else {
            implementationField = new SDField(schema.getDocument(), field.getName() + "_" + suffix, DataType.STRING);
        }
        implementationField.setRankType(RankType.EMPTY);
        implementationField.setStemming(Stemming.NONE);
        implementationField.getNormalizing().inferCodepoint();
        implementationField.parseIndexingScript(schema.getName(), indexing);
        String indexName = field.getName();
        String implementationIndexName = indexName + "_" + suffix;
        Index implementationIndex = new Index(implementationIndexName);
        schema.addIndex(implementationIndex);
        if (queryCommand != null) {
            field.addQueryCommand(queryCommand);
        }
        schema.addExtraField(implementationField);
        schema.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, implementationField.getName());
        return implementationField;
    }

    /**
     * Returns an iterator of all the rank settings with given type in all the rank  profiles in this search
     * definition.
     */
    protected Iterator<RankProfile.RankSetting> matchingRankSettingsIterator(Schema schema,
                                                                             RankProfile.RankSetting.Type type)
    {
        List<RankProfile.RankSetting> someRankSettings = new java.util.ArrayList<>();

        for (RankProfile profile : rankProfileRegistry.rankProfilesOf(schema)) {
            for (Iterator<RankProfile.RankSetting> j = profile.declaredRankSettingIterator(); j.hasNext(); ) {
                RankProfile.RankSetting setting = j.next();
                if (setting.getType().equals(type)) {
                    someRankSettings.add(setting);
                }
            }
        }
        return someRankSettings.iterator();
    }

    protected String formatError(String schemaName, String fieldName, String msg) {
        return "For schema '" + schemaName + "', field '" + fieldName + "': " + msg;
    }

    protected RuntimeException newProcessException(String schemaName, String fieldName, String msg) {
        return new IllegalArgumentException(formatError(schemaName, fieldName, msg));
    }

    protected RuntimeException newProcessException(Schema schema, FieldBase field, String msg) {
        return newProcessException(schema.getName(), field.getName(), msg);
    }

    public void fail(Schema schema, Field field, String msg) {
        throw newProcessException(schema, field, msg);
    }

    protected void warn(String schemaName, String fieldName, String message) {
        String fullMsg = formatError(schemaName, fieldName, message);
        deployLogger.logApplicationPackage(Level.WARNING, fullMsg);
    }

    protected void warn(Schema schema, Field field, String message) {
        warn(schema.getName(), field.getName(), message);
    }

    protected void info(String schemaName, String fieldName, String message) {
        String fullMsg = formatError(schemaName, fieldName, message);
        deployLogger.logApplicationPackage(Level.INFO, fullMsg);
    }

    protected void info(Schema schema, Field field, String message) {
        info(schema.getName(), field.getName(), message);
    }

}
