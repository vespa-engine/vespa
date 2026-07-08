// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.processing.multifieldresolver.RankProfileTypeSettingsProcessor;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.TestProperties;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Executor of processors. This defines the right order of processor execution.
 *
 * @author bratseth
 * @author bjorncs
 */
public class Processing {

    private final ModelContext.Properties properties;

    public Processing(ModelContext.Properties properties) { this.properties = properties; }

    private Collection<ProcessorFactory> processors() {
        return List.<ProcessorFactory>of(
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
                DictionaryProcessor::new,
                WordMatch::new,
                ImportedFieldsResolver::new,
                AddDataTypeAndTransformToSummaryOfImportedFields::new,
                ImplicitSummaries::new,
                ImplicitSummaryFields::new,
                AdjustPositionSummaryFields::new,
                SummaryConsistency::new,
                AdjustSummaryTransforms::new,
                SummaryNamesFieldCollisions::new,
                SummaryFieldsMustHaveValidSource::new,
                TokensTransformValidator::new,
                SummaryElementsSelectorValidator::new,
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
                ValidateNoFieldRankFilterOverlap::new,
                MatchConsistency::new,
                ValidateStructTypeInheritance::new,
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
                SingleValueOnlyAttributeValidator::new,
                PagedAttributeValidator::new,
                // These should be last:
                IndexingValidation::new,
                IndexingValues::new,
                RankProfileValidator::new);
    }

    /** Processors of rank profiles only (those who tolerate and do something useful when the search field is null) */
    private Collection<ProcessorFactory> rankProfileProcessors() {
        return List.of(
                RankProfileTypeSettingsProcessor::new,
                ReservedFunctionNames::new,
                RankingExpressionTypeResolver::new);
    }

    private void runProcessor(Processor processor, boolean validate, boolean documentsOnly) {
        processor.process(validate, documentsOnly, properties);
    }

    /**
     * Runs all search processors on the given {@link Schema} object. These will modify the search object, <b>possibly
     * exchanging it with another</b>, as well as its document types.
     *
     * @param schema the search to process
     * @param deployLogger the log to log messages and warnings for application deployment to
     * @param rankProfileRegistry a {@link com.yahoo.schema.RankProfileRegistry}
     * @param queryProfiles the query profiles contained in the application this search is part of
     * @param processorsToSkip a set of processor classes we should not invoke in this. Useful for testing.
     */
    public void process(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry,
                        QueryProfiles queryProfiles, boolean validate, boolean documentsOnly,
                        Set<Class<? extends Processor>> processorsToSkip)
    {
        Collection<ProcessorFactory> factories = processors();
        factories.stream()
                .map(factory -> factory.create(schema, deployLogger, rankProfileRegistry, queryProfiles))
                .filter(processor -> ! processorsToSkip.contains(processor.getClass()))
                .forEach(processor -> runProcessor(processor, validate, documentsOnly));
    }

    /**
     * Runs rank profiles processors only.
     *
     * @param deployLogger the log to log messages and warnings for application deployment to
     * @param rankProfileRegistry a {@link com.yahoo.schema.RankProfileRegistry}
     * @param queryProfiles the query profiles contained in the application this search is part of
     */
    public void processRankProfiles(DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry,
                                    QueryProfiles queryProfiles, boolean validate, boolean documentsOnly) {
        Collection<ProcessorFactory> factories = rankProfileProcessors();
        factories.stream()
                 .map(factory -> factory.create(null, deployLogger, rankProfileRegistry, queryProfiles))
                 .forEach(processor -> runProcessor(processor, validate, documentsOnly));
    }

    @FunctionalInterface
    public interface ProcessorFactory {
        Processor create(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry,
                         QueryProfiles queryProfiles);
    }

}
