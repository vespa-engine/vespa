// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.google.common.collect.ImmutableList;
import com.yahoo.collections.Pair;
import com.yahoo.compress.Compressor;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.OnnxModel;
import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.expressiontransforms.OnnxModelTransformer;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A rank profile derived from a search definition, containing exactly the features available natively in the server
 *
 * @author bratseth
 */
public class RawRankProfile implements RankProfilesConfig.Producer {

    /** A reusable compressor with default settings */
    private static final Compressor compressor = new Compressor();

    private final String keyEndMarker = "\r=";
    private final String valueEndMarker = "\r\n";

    // TODO: These are to expose coupling between the strings used here and elsewhere
    public final static String summaryFeatureFefPropertyPrefix = "vespa.summary.feature";
    public final static String rankFeatureFefPropertyPrefix = "vespa.dump.feature";

    private final String name;

    private final Compressor.Compression compressedProperties;

    /**
     * Creates a raw rank profile from the given rank profile
     */
    public RawRankProfile(RankProfile rankProfile, QueryProfileRegistry queryProfiles, ImportedMlModels importedModels, AttributeFields attributeFields, ModelContext.Properties deployProperties) {
        this.name = rankProfile.getName();
        compressedProperties = compress(new Deriver(rankProfile, queryProfiles, importedModels, attributeFields, deployProperties).derive());
    }

    /**
     * Only for testing
     */
    public RawRankProfile(RankProfile rankProfile, QueryProfileRegistry queryProfiles, ImportedMlModels importedModels, AttributeFields attributeFields) {
        this(rankProfile, queryProfiles, importedModels, attributeFields, new TestProperties());
    }

    private Compressor.Compression compress(List<Pair<String, String>> properties) {
        StringBuilder b = new StringBuilder();
        for (Pair<String, String> property : properties)
            b.append(property.getFirst()).append(keyEndMarker).append(property.getSecond()).append(valueEndMarker);
        return compressor.compress(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<Pair<String, String>> decompress(Compressor.Compression compression) {
        String propertiesString = new String(compressor.decompress(compression), StandardCharsets.UTF_8);
        if (propertiesString.isEmpty()) return ImmutableList.of();

        ImmutableList.Builder<Pair<String, String>> properties = new ImmutableList.Builder<>();
        for (int pos = 0; pos < propertiesString.length();) {
            int keyEndPos = propertiesString.indexOf(keyEndMarker, pos);
            String key = propertiesString.substring(pos, keyEndPos);
            pos = keyEndPos + keyEndMarker.length();
            int valueEndPos = propertiesString.indexOf(valueEndMarker, pos);
            String value = propertiesString.substring(pos, valueEndPos);
            pos = valueEndPos + valueEndMarker.length();
            properties.add(new Pair<>(key, value));
        }
        return properties.build();
    }

    public String getName() { return name; }

    @Override
    public String toString() {
        return " rank profile " + name;
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        RankProfilesConfig.Rankprofile.Builder b = new RankProfilesConfig.Rankprofile.Builder().name(getName());
        getRankProperties(b);
        builder.rankprofile(b);
    }

    private void getRankProperties(RankProfilesConfig.Rankprofile.Builder b) {
        RankProfilesConfig.Rankprofile.Fef.Builder fefB = new RankProfilesConfig.Rankprofile.Fef.Builder();
        for (Pair<String, String> p : decompress(compressedProperties))
            fefB.property(new RankProfilesConfig.Rankprofile.Fef.Property.Builder().name(p.getFirst()).value(p.getSecond()));
        b.fef(fefB);
    }

    /**
     * Returns the properties of this as an unmodifiable list.
     * Note: This method is expensive.
     */
    public List<Pair<String, String>> configProperties() { return decompress(compressedProperties); }

    private static class Deriver {

        /**
         * The field rank settings of this profile
         */
        private Map<String, FieldRankSettings> fieldRankSettings = new java.util.LinkedHashMap<>();

        private RankingExpression firstPhaseRanking = null;

        private RankingExpression secondPhaseRanking = null;

        private Set<ReferenceNode> summaryFeatures = new LinkedHashSet<>();

        private Set<ReferenceNode> rankFeatures = new LinkedHashSet<>();

        private List<RankProfile.RankProperty> rankProperties = new ArrayList<>();

        /**
         * Rank properties for weight settings to make these available to feature executors
         */
        private List<RankProfile.RankProperty> boostAndWeightRankProperties = new ArrayList<>();

        private boolean ignoreDefaultRankFeatures = false;

        private RankProfile.MatchPhaseSettings matchPhaseSettings = null;

        private int rerankCount = -1;
        private int keepRankCount = -1;
        private int numThreadsPerSearch = -1;
        private int minHitsPerThread = -1;
        private int numSearchPartitions = -1;
        private double termwiseLimit = 1.0;
        private double rankScoreDropLimit = -Double.MAX_VALUE;

        /**
         * The rank type definitions used to derive settings for the native rank features
         */
        private final NativeRankTypeDefinitionSet nativeRankTypeDefinitions = new NativeRankTypeDefinitionSet("default");

        private final Map<String, String> attributeTypes;
        private final Map<String, String> queryFeatureTypes;

        private Set<String> filterFields = new java.util.LinkedHashSet<>();

        /**
         * Creates a raw rank profile from the given rank profile
         */
        Deriver(RankProfile rankProfile, QueryProfileRegistry queryProfiles, ImportedMlModels importedModels,
                       AttributeFields attributeFields, ModelContext.Properties deployProperties)
        {
            RankProfile compiled = rankProfile.compile(queryProfiles, importedModels);
            attributeTypes = compiled.getAttributeTypes();
            queryFeatureTypes = compiled.getQueryFeatureTypes();
            deriveRankingFeatures(compiled, deployProperties);
            deriveRankTypeSetting(compiled, attributeFields);
            deriveFilterFields(compiled);
            deriveWeightProperties(compiled);
        }

        private void deriveFilterFields(RankProfile rp) {
            filterFields.addAll(rp.allFilterFields());
        }

        private void deriveRankingFeatures(RankProfile rankProfile, ModelContext.Properties deployProperties) {
            firstPhaseRanking = rankProfile.getFirstPhaseRanking();
            secondPhaseRanking = rankProfile.getSecondPhaseRanking();
            summaryFeatures = new LinkedHashSet<>(rankProfile.getSummaryFeatures());
            rankFeatures = rankProfile.getRankFeatures();
            rerankCount = rankProfile.getRerankCount();
            matchPhaseSettings = rankProfile.getMatchPhaseSettings();
            numThreadsPerSearch = rankProfile.getNumThreadsPerSearch();
            minHitsPerThread = rankProfile.getMinHitsPerThread();
            numSearchPartitions = rankProfile.getNumSearchPartitions();
            termwiseLimit = rankProfile.getTermwiseLimit().orElse(deployProperties.featureFlags().defaultTermwiseLimit());
            keepRankCount = rankProfile.getKeepRankCount();
            rankScoreDropLimit = rankProfile.getRankScoreDropLimit();
            ignoreDefaultRankFeatures = rankProfile.getIgnoreDefaultRankFeatures();
            rankProperties = new ArrayList<>(rankProfile.getRankProperties());
            derivePropertiesAndSummaryFeaturesFromFunctions(rankProfile.getFunctions());
            deriveOnnxModelFunctionsAndSummaryFeatures(rankProfile);
        }

        private void derivePropertiesAndSummaryFeaturesFromFunctions(Map<String, RankProfile.RankingExpressionFunction> functions) {
            if (functions.isEmpty()) return;

            List<ExpressionFunction> functionExpressions = functions.values().stream().map(f -> f.function()).collect(Collectors.toList());
            Map<String, String> functionProperties = new LinkedHashMap<>();

            if (firstPhaseRanking != null) {
                functionProperties.putAll(firstPhaseRanking.getRankProperties(functionExpressions));
            }
            if (secondPhaseRanking != null) {
                functionProperties.putAll(secondPhaseRanking.getRankProperties(functionExpressions));
            }

            SerializationContext context = new SerializationContext(functionExpressions, null, functionProperties);
            replaceFunctionSummaryFeatures(context);

            // First phase, second phase and summary features should add all required functions to the context.
            // However, we need to add any functions not referenced in those anyway for model-evaluation.
            deriveFunctionProperties(functions, functionExpressions, functionProperties);

            for (Map.Entry<String, String> e : functionProperties.entrySet()) {
                rankProperties.add(new RankProfile.RankProperty(e.getKey(), e.getValue()));
            }
        }

        private void deriveFunctionProperties(Map<String, RankProfile.RankingExpressionFunction> functions,
                                              List<ExpressionFunction> functionExpressions,
                                              Map<String, String> functionProperties) {
            SerializationContext context = new SerializationContext(functionExpressions, null, functionProperties);
            for (Map.Entry<String, RankProfile.RankingExpressionFunction> e : functions.entrySet()) {
                String propertyName = RankingExpression.propertyName(e.getKey());
                if (context.serializedFunctions().containsKey(propertyName)) {
                    continue;
                }
                String expressionString = e.getValue().function().getBody().getRoot().toString(new StringBuilder(), context, null, null).toString();

                context.addFunctionSerialization(RankingExpression.propertyName(e.getKey()), expressionString);
                for (Map.Entry<String, TensorType> argumentType : e.getValue().function().argumentTypes().entrySet())
                    context.addArgumentTypeSerialization(e.getKey(), argumentType.getKey(), argumentType.getValue());
                if (e.getValue().function().returnType().isPresent())
                    context.addFunctionTypeSerialization(e.getKey(), e.getValue().function().returnType().get());
                // else if (e.getValue().function().arguments().isEmpty()) TODO: Enable this check when we resolve all types
                //     throw new IllegalStateException("Type of function '" + e.getKey() + "' is not resolved");
            }
            functionProperties.putAll(context.serializedFunctions());
        }

        private void replaceFunctionSummaryFeatures(SerializationContext context) {
            if (summaryFeatures == null) return;
            Map<String, ReferenceNode> functionSummaryFeatures = new LinkedHashMap<>();
            for (Iterator<ReferenceNode> i = summaryFeatures.iterator(); i.hasNext(); ) {
                ReferenceNode referenceNode = i.next();
                // Is the feature a function?
                ExpressionFunction function = context.getFunction(referenceNode.getName());
                if (function != null) {
                    String propertyName = RankingExpression.propertyName(referenceNode.getName());
                    String expressionString = function.getBody().getRoot().toString(new StringBuilder(), context, null, null).toString();
                    context.addFunctionSerialization(propertyName, expressionString);
                    ReferenceNode newReferenceNode = new ReferenceNode("rankingExpression(" + referenceNode.getName() + ")", referenceNode.getArguments().expressions(), referenceNode.getOutput());
                    functionSummaryFeatures.put(referenceNode.getName(), newReferenceNode);
                    i.remove(); // Will add the expanded one in next block
                }
            }
            // Then, replace the summary features that were functions
            for (Map.Entry<String, ReferenceNode> e : functionSummaryFeatures.entrySet()) {
                summaryFeatures.add(e.getValue());
            }
        }

        private void deriveWeightProperties(RankProfile rankProfile) {

            for (RankProfile.RankSetting setting : rankProfile.rankSettings()) {
                if (!setting.getType().equals(RankProfile.RankSetting.Type.WEIGHT)) {
                    continue;
                }
                boostAndWeightRankProperties.add(new RankProfile.RankProperty("vespa.fieldweight." + setting.getFieldName(),
                        String.valueOf(setting.getIntValue())));
            }
        }

        /**
         * Adds the type boosts from a rank profile
         */
        private void deriveRankTypeSetting(RankProfile rankProfile, AttributeFields attributeFields) {
            for (Iterator<RankProfile.RankSetting> i = rankProfile.rankSettingIterator(); i.hasNext(); ) {
                RankProfile.RankSetting setting = i.next();
                if (!setting.getType().equals(RankProfile.RankSetting.Type.RANKTYPE)) continue;

                deriveNativeRankTypeSetting(setting.getFieldName(), (RankType) setting.getValue(), attributeFields,
                        hasDefaultRankTypeSetting(rankProfile, setting.getFieldName()));
            }
        }

        private void deriveNativeRankTypeSetting(String fieldName, RankType rankType, AttributeFields attributeFields, boolean isDefaultSetting) {
            if (isDefaultSetting) return;

            NativeRankTypeDefinition definition = nativeRankTypeDefinitions.getRankTypeDefinition(rankType);
            if (definition == null) throw new IllegalArgumentException("In field '" + fieldName + "': " +
                                                                       rankType + " is known but has no implementation. " +
                                                                       "Supported rank types: " +
                                                                       nativeRankTypeDefinitions.types().keySet());

            FieldRankSettings settings = deriveFieldRankSettings(fieldName);
            for (Iterator<NativeTable> i = definition.rankSettingIterator(); i.hasNext(); ) {
                NativeTable table = i.next();
                // only add index field tables if we are processing an index field and
                // only add attribute field tables if we are processing an attribute field
                if ((FieldRankSettings.isIndexFieldTable(table) && attributeFields.getAttribute(fieldName) == null) ||
                    (FieldRankSettings.isAttributeFieldTable(table) && attributeFields.getAttribute(fieldName) != null)) {
                    settings.addTable(table);
                }
            }
        }

        private boolean hasDefaultRankTypeSetting(RankProfile rankProfile, String fieldName) {
            RankProfile.RankSetting setting =
                    rankProfile.getRankSetting(fieldName, RankProfile.RankSetting.Type.RANKTYPE);
            return setting != null && setting.getValue().equals(RankType.DEFAULT);
        }

        private FieldRankSettings deriveFieldRankSettings(String fieldName) {
            FieldRankSettings settings = fieldRankSettings.get(fieldName);
            if (settings == null) {
                settings = new FieldRankSettings(fieldName);
                fieldRankSettings.put(fieldName, settings);
            }
            return settings;
        }

        /** Derives the properties this produces */
        public List<Pair<String, String>>  derive() {
            List<Pair<String, String>>  properties = new ArrayList<>();
            for (RankProfile.RankProperty property : rankProperties) {
                if ("rankingExpression(firstphase).rankingScript".equals(property.getName())) {
                    // Could have been set by function expansion. Set expressions, then skip this property.
                    try {
                        firstPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse first phase expression", e);
                    }
                }
                else if ("rankingExpression(secondphase).rankingScript".equals(property.getName())) {
                    try {
                        secondPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse second phase expression", e);
                    }
                }
                else {
                    properties.add(new Pair<>(property.getName(), property.getValue()));
                }
            }
            properties.addAll(deriveRankingPhaseRankProperties(firstPhaseRanking, "firstphase"));
            properties.addAll(deriveRankingPhaseRankProperties(secondPhaseRanking, "secondphase"));
            for (FieldRankSettings settings : fieldRankSettings.values()) {
                properties.addAll(settings.deriveRankProperties());
            }
            for (RankProfile.RankProperty property : boostAndWeightRankProperties) {
                properties.add(new Pair<>(property.getName(), property.getValue()));
            }
            for (ReferenceNode feature : summaryFeatures) {
                properties.add(new Pair<>(summaryFeatureFefPropertyPrefix, feature.toString()));
            }
            for (ReferenceNode feature : rankFeatures) {
                properties.add(new Pair<>(rankFeatureFefPropertyPrefix, feature.toString()));
            }
            if (numThreadsPerSearch > 0) {
                properties.add(new Pair<>("vespa.matching.numthreadspersearch", numThreadsPerSearch + ""));
            }
            if (minHitsPerThread > 0) {
                properties.add(new Pair<>("vespa.matching.minhitsperthread", minHitsPerThread + ""));
            }
            if (numSearchPartitions >= 0) {
                properties.add(new Pair<>("vespa.matching.numsearchpartitions", numSearchPartitions + ""));
            }
            if (termwiseLimit < 1.0) {
                properties.add(new Pair<>("vespa.matching.termwise_limit", termwiseLimit + ""));
            }
            if (matchPhaseSettings != null) {
                properties.add(new Pair<>("vespa.matchphase.degradation.attribute", matchPhaseSettings.getAttribute()));
                properties.add(new Pair<>("vespa.matchphase.degradation.ascendingorder", matchPhaseSettings.getAscending() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.maxhits", matchPhaseSettings.getMaxHits() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.maxfiltercoverage", matchPhaseSettings.getMaxFilterCoverage() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.samplepercentage", matchPhaseSettings.getEvaluationPoint() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.postfiltermultiplier", matchPhaseSettings.getPrePostFilterTippingPoint() + ""));
                RankProfile.DiversitySettings diversitySettings = matchPhaseSettings.getDiversity();
                if (diversitySettings != null) {
                    properties.add(new Pair<>("vespa.matchphase.diversity.attribute", diversitySettings.getAttribute()));
                    properties.add(new Pair<>("vespa.matchphase.diversity.mingroups", String.valueOf(diversitySettings.getMinGroups())));
                    properties.add(new Pair<>("vespa.matchphase.diversity.cutoff.factor", String.valueOf(diversitySettings.getCutoffFactor())));
                    properties.add(new Pair<>("vespa.matchphase.diversity.cutoff.strategy", String.valueOf(diversitySettings.getCutoffStrategy())));
                }
            }
            if (rerankCount > -1) {
                properties.add(new Pair<>("vespa.hitcollector.heapsize", rerankCount + ""));
            }
            if (keepRankCount > -1) {
                properties.add(new Pair<>("vespa.hitcollector.arraysize", keepRankCount + ""));
            }
            if (rankScoreDropLimit > -Double.MAX_VALUE) {
                properties.add(new Pair<>("vespa.hitcollector.rankscoredroplimit", rankScoreDropLimit + ""));
            }
            if (ignoreDefaultRankFeatures) {
                properties.add(new Pair<>("vespa.dump.ignoredefaultfeatures", String.valueOf(true)));
            }
            Iterator filterFieldsIterator = filterFields.iterator();
            while (filterFieldsIterator.hasNext()) {
                String fieldName = (String) filterFieldsIterator.next();
                properties.add(new Pair<>("vespa.isfilterfield." + fieldName, String.valueOf(true)));
            }
            for (Map.Entry<String, String> attributeType : attributeTypes.entrySet()) {
                properties.add(new Pair<>("vespa.type.attribute." + attributeType.getKey(), attributeType.getValue()));
            }
            for (Map.Entry<String, String> queryFeatureType : queryFeatureTypes.entrySet()) {
                properties.add(new Pair<>("vespa.type.query." + queryFeatureType.getKey(), queryFeatureType.getValue()));
            }
            if (properties.size() >= 1000000) throw new RuntimeException("Too many rank properties");
            return properties;
        }

        private List<Pair<String, String>> deriveRankingPhaseRankProperties(RankingExpression expression, String phase) {
            List<Pair<String, String>> properties = new ArrayList<>();
            if (expression == null) return properties;

            String name = expression.getName();
            if ("".equals(name))
                name = phase;

            if (expression.getRoot() instanceof ReferenceNode) {
                properties.add(new Pair<>("vespa.rank." + phase, expression.getRoot().toString()));
            } else {
                properties.add(new Pair<>("vespa.rank." + phase, "rankingExpression(" + name + ")"));
                properties.add(new Pair<>("rankingExpression(" + name + ").rankingScript", expression.getRoot().toString()));
            }
            return properties;
        }

        private void deriveOnnxModelFunctionsAndSummaryFeatures(RankProfile rankProfile) {
            if (rankProfile.getSearch() == null) return;
            if (rankProfile.getSearch().onnxModels().asMap().isEmpty()) return;
            replaceOnnxFunctionInputs(rankProfile);
            replaceImplicitOnnxConfigSummaryFeatures(rankProfile);
        }

        private void replaceOnnxFunctionInputs(RankProfile rankProfile) {
            Set<String> functionNames = rankProfile.getFunctions().keySet();
            if (functionNames.isEmpty()) return;
            for (OnnxModel onnxModel: rankProfile.getSearch().onnxModels().asMap().values()) {
                for (Map.Entry<String, String> mapping : onnxModel.getInputMap().entrySet()) {
                    String source = mapping.getValue();
                    if (functionNames.contains(source)) {
                        onnxModel.addInputNameMapping(mapping.getKey(), "rankingExpression(" + source + ")");
                    }
                }
            }
        }

        private void replaceImplicitOnnxConfigSummaryFeatures(RankProfile rankProfile) {
            if (summaryFeatures == null || summaryFeatures.isEmpty()) return;
            Set<ReferenceNode> replacedSummaryFeatures = new HashSet<>();
            for (Iterator<ReferenceNode> i = summaryFeatures.iterator(); i.hasNext(); ) {
                ReferenceNode referenceNode = i.next();
                ReferenceNode replacedNode = (ReferenceNode) OnnxModelTransformer.transformFeature(referenceNode, rankProfile);
                if (referenceNode != replacedNode) {
                    replacedSummaryFeatures.add(replacedNode);
                    i.remove();
                }
            }
            summaryFeatures.addAll(replacedSummaryFeatures);
        }

    }

}
