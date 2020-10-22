// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.processing.multifieldresolver.RankProfileTypeSettingsProcessor;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Arrays;
import java.util.Collection;

/**
 * Executor of processors. This defines the right order of processor execution.
 *
 * @author bratseth
 * @author bjorncs
 */
public class Processing {

    private Collection<ProcessorFactory> processors() {
        return Arrays.asList(
                SearchMustHaveDocument::new,
                UrlFieldValidator::new,
                BuiltInFieldSets::new,
                ReservedDocumentNames::new,
                IndexFieldNames::new,
                IntegerIndex2Attribute::new,
                MakeAliases::new,
                UriHack::new,
                LiteralBoost::new,
                TagType::new,
                ValidateFieldTypesDocumentsOnly::new,
                IndexingInputs::new,
                OptimizeIlscript::new,
                ValidateFieldWithIndexSettingsCreatesIndex::new,
                AttributesImplicitWord::new,
                MutableAttributes::new,
                CreatePositionZCurve::new,
                WordMatch::new,
                ImportedFieldsResolver::new,
                ImplicitSummaries::new,
                ImplicitSummaryFields::new,
                AdjustPositionSummaryFields::new,
                SummaryConsistency::new,
                SummaryNamesFieldCollisions::new,
                SummaryFieldsMustHaveValidSource::new,
                MatchedElementsOnlyResolver::new,
                AddAttributeTransformToSummaryOfImportedFields::new,
                MakeDefaultSummaryTheSuperSet::new,
                Bolding::new,
                AttributeProperties::new,
                SetRankTypeEmptyOnFilters::new,
                SummaryDynamicStructsArrays::new,
                StringSettingsOnNonStringFields::new,
                IndexingOutputs::new,
                ExactMatch::new,
                NGramMatch::new,
                TextMatch::new,
                MultifieldIndexHarmonizer::new,
                FilterFieldNames::new,
                MatchConsistency::new,
                ValidateFieldTypes::new,
                SummaryDiskAccessValidator::new,
                DisallowComplexMapAndWsetKeyTypes::new,
                SortingSettings::new,
                FieldSetSettings::new,
                AddExtraFieldsToDocument::new,
                PredicateProcessor::new,
                MatchPhaseSettingsValidator::new,
                DiversitySettingsValidator::new,
                TensorFieldProcessor::new,
                RankProfileTypeSettingsProcessor::new,
                ReferenceFieldsProcessor::new,
                FastAccessValidator::new,
                ReservedFunctionNames::new,
                OnnxModelConfigGenerator::new,
                OnnxModelTypeResolver::new,
                RankingExpressionTypeResolver::new,
                // These should be last:
                IndexingValidation::new,
                IndexingValues::new);
    }

    /** Processors of rank profiles only (those who tolerate and do something useful when the search field is null) */
    private Collection<ProcessorFactory> rankProfileProcessors() {
        return Arrays.asList(
                RankProfileTypeSettingsProcessor::new,
                ReservedFunctionNames::new,
                RankingExpressionTypeResolver::new);
    }

    /**
     * Runs all search processors on the given {@link Search} object. These will modify the search object, <b>possibly
     * exchanging it with another</b>, as well as its document types.
     *
     * @param search The search to process.
     * @param deployLogger The log to log messages and warnings for application deployment to
     * @param rankProfileRegistry a {@link com.yahoo.searchdefinition.RankProfileRegistry}
     * @param queryProfiles The query profiles contained in the application this search is part of.
     */
    public void process(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry,
                        QueryProfiles queryProfiles, boolean validate, boolean documentsOnly) {
        Collection<ProcessorFactory> factories = processors();
        factories.stream()
                .map(factory -> factory.create(search, deployLogger, rankProfileRegistry, queryProfiles))
                .forEach(processor -> processor.process(validate, documentsOnly));
    }

    /**
     * Runs rank profiles processors only.
     *
     * @param deployLogger The log to log messages and warnings for application deployment to
     * @param rankProfileRegistry a {@link com.yahoo.searchdefinition.RankProfileRegistry}
     * @param queryProfiles The query profiles contained in the application this search is part of.
     */
    public void processRankProfiles(DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry,
                        QueryProfiles queryProfiles, boolean validate, boolean documentsOnly) {
        Collection<ProcessorFactory> factories = rankProfileProcessors();
        factories.stream()
                 .map(factory -> factory.create(null, deployLogger, rankProfileRegistry, queryProfiles))
                 .forEach(processor -> processor.process(validate, documentsOnly));
    }

    @FunctionalInterface
    public interface ProcessorFactory {
        Processor create(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles);
    }

}
