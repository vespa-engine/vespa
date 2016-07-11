// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.processing.multifieldresolver.RankProfileTypeSettingsProcessor;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Executor of processors. This defines the right order of processor execution.
 *
 * @author bratseth
 */
public class Processing {

    /**
     * Runs all search processors on the given {@link Search} object. These will modify the search object, <b>possibly
     * exchanging it with another</b>, as well as its document types.
     *
     * @param search The search to process.
     * @param deployLogger The log to log messages and warnings for application deployment to
     * @param rankProfileRegistry a {@link com.yahoo.searchdefinition.RankProfileRegistry}
     * @param queryProfiles The query profiles contained in the application this search is part of.
     */
    public static void process(Search search,
                               DeployLogger deployLogger,
                               RankProfileRegistry rankProfileRegistry,
                               QueryProfiles queryProfiles) {
        search.process();
        new SearchMustHaveDocument(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new UrlFieldValidator(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new BuiltInFieldSets(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ReservedDocumentNames(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexFieldNames(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IntegerIndex2Attribute(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new MakeAliases(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SetLanguage(search, deployLogger, rankProfileRegistry, queryProfiles).process(); // Needs to come before UriHack, see ticket 6405470
        new UriHack(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new LiteralBoost(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexTo2FieldSet(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new TagType(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexingInputs(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new OptimizeIlscript(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ValidateFieldWithIndexSettingsCreatesIndex(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new AttributesImplicitWord(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new CreatePositionZCurve(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new WordMatch(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new DeprecateAttributePrefetch(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ImplicitSummaries(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ImplicitSummaryFields(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SummaryConsistency(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SummaryNamesFieldCollisions(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SummaryFieldsMustHaveValidSource(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new MakeDefaultSummaryTheSuperSet(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new Bolding(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new AttributeProperties(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SetRankTypeEmptyOnFilters(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexSettingsNonFieldNames(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SummaryDynamicStructsArrays(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new StringSettingsOnNonStringFields(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexingOutputs(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ExactMatch(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new NGramMatch(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new TextMatch(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new MultifieldIndexHarmonizer(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new FilterFieldNames(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new MatchConsistency(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new ValidateFieldTypes(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new DisallowComplexMapAndWsetKeyTypes(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new SortingSettings(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new FieldSetValidity(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new AddExtraFieldsToDocument(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new PredicateProcessor(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new MatchPhaseSettingsValidator(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new DiversitySettingsValidator(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new TensorFieldProcessor(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new RankProfileTypeSettingsProcessor(search, deployLogger, rankProfileRegistry, queryProfiles).process();

        // These two should be last.
        new IndexingValidation(search, deployLogger, rankProfileRegistry, queryProfiles).process();
        new IndexingValues(search, deployLogger, rankProfileRegistry, queryProfiles).process();
    }
}
