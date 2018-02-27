// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.Stemming;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

/**
 * Abstract superclass of all search definition processors.
 *
 * @author bratseth
 */
public abstract class Processor {

    protected final Search search;
    protected DeployLogger deployLogger;
    protected final RankProfileRegistry rankProfileRegistry;
    protected final QueryProfiles queryProfiles;

    /**
     * Base constructor
     *
     * @param search the search to process
     * @param deployLogger Logger du use when logging deploy output.
     * @param rankProfileRegistry Registry with all rank profiles, used for lookup and insertion.
     * @param queryProfiles The query profiles contained in the application this search is part of.
     */
    public Processor(Search search,
                     DeployLogger deployLogger,
                     RankProfileRegistry rankProfileRegistry,
                     QueryProfiles queryProfiles) {
        this.search = search;
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
     */
    public abstract void process(boolean validate);

    /**
     * Convenience method for adding a no-strings-attached implementation field for a regular field
     *
     * @param search       the search definition in question
     * @param field        the field to add an implementation field for
     * @param suffix       the suffix of the added implementation field (without the underscore)
     * @param indexing     the indexing statement of the field
     * @param queryCommand the query command of the original field, or null if none
     * @return the implementation field which is added to the search
     */
    protected SDField addField(Search search, SDField field, String suffix, String indexing, String queryCommand) {
        SDField implementationField = search.getConcreteField(field.getName() + "_" + suffix);
        if (implementationField != null) {
            deployLogger.log(Level.WARNING, "Implementation field " + implementationField + " added twice");
        } else {
            implementationField = new SDField(search.getDocument(), field.getName() + "_" + suffix, DataType.STRING);
        }
        implementationField.setRankType(RankType.EMPTY);
        implementationField.setStemming(Stemming.NONE);
        implementationField.getNormalizing().inferCodepoint();
        implementationField.parseIndexingScript(indexing);
        for (Iterator i = field.getFieldNameAsIterator(); i.hasNext();) {
            String indexName = (String)i.next();
            String implementationIndexName = indexName + "_" + suffix;
            Index implementationIndex = new Index(implementationIndexName);
            search.addIndex(implementationIndex);
        }
        if (queryCommand != null) {
            field.addQueryCommand(queryCommand);
        }
        search.addExtraField(implementationField);
        search.fieldSets().addBuiltInFieldSetItem(BuiltInFieldSets.INTERNAL_FIELDSET_NAME, implementationField.getName());
        return implementationField;
    }

    /**
     * Returns an iterator of all the rank settings with given type in all the rank  profiles in this search
     * definition.
     */
    protected Iterator<RankProfile.RankSetting> matchingRankSettingsIterator(
            Search search, RankProfile.RankSetting.Type type)
    {
        List<RankProfile.RankSetting> someRankSettings = new java.util.ArrayList<>();

        for (RankProfile profile : rankProfileRegistry.localRankProfiles(search)) {
            for (Iterator j = profile.declaredRankSettingIterator(); j.hasNext(); ) {
                RankProfile.RankSetting setting = (RankProfile.RankSetting)j.next();
                if (setting.getType().equals(type)) {
                    someRankSettings.add(setting);
                }
            }
        }
        return someRankSettings.iterator();
    }

    protected String formatError(String searchName, String fieldName, String msg) {
        return "For search '" + searchName + "', field '" + fieldName + "': " + msg;
    }

    protected RuntimeException newProcessException(String searchName, String fieldName, String msg) {
        return new IllegalArgumentException(formatError(searchName, fieldName, msg));
    }

    protected RuntimeException newProcessException(Search search, Field field, String msg) {
        return newProcessException(search.getName(), field.getName(), msg);
    }

    public void fail(Search search, Field field, String msg) {
        throw newProcessException(search, field, msg);
    }

    protected void warn(String searchName, String fieldName, String msg) {
        String fullMsg = formatError(searchName, fieldName, msg);
        deployLogger.log(Level.WARNING, fullMsg);
    }

    protected void warn(Search search, Field field, String msg) {
        warn(search.getName(), field.getName(), msg);
    }
}
