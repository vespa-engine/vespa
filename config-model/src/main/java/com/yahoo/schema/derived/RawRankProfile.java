// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.yahoo.collections.Pair;
import com.yahoo.compress.Compressor;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchlib.ranking.features.FeatureNames;
import com.yahoo.search.query.ranking.ElementGap;
import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.LargeRankingExpressions;
import com.yahoo.schema.RankingExpressionBody;
import com.yahoo.schema.document.RankType;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.expressiontransforms.OnnxModelTransformer;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.tensor.evaluation.TypeContext;
import com.yahoo.text.Text;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import static com.yahoo.searchlib.rankingexpression.Reference.wrapInRankingExpression;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * A rank profile derived from a search definition, containing exactly the features available natively in the server
 * RawRankProfile has a long lifetime so do not refer objects not necessary.
 *
 * @author bratseth
 */
public class RawRankProfile {

    /** A reusable compressor with default settings */
    private static final Compressor compressor = new Compressor();

    private static final String keyEndMarker = "\r=";
    private static final String valueEndMarker = "\r\n";

    private final String name;
    private final Compressor.Compression compressedProperties;
    private final Map<String, RankProfile.RankFeatureNormalizer> featureNormalizers;

    /**  The compiled profile this is created from. */
    private final Collection<RankProfile.Constant> constants;
    private final Collection<OnnxModel> onnxModels;

    /** Creates a raw rank profile from the given rank profile. */
    public RawRankProfile(RankProfile rankProfile, LargeRankingExpressions largeExpressions,
                          QueryProfileRegistry queryProfiles, ImportedMlModels importedModels,
                          AttributeFields attributeFields, ModelContext.Properties deployProperties) {
        this.name = rankProfile.name();
        /*
         * Forget the RankProfiles as soon as possible. They can become very large and memory hungry
         * Especially do not refer them through any member variables due to the RawRankProfile living forever.
         */
        RankProfile compiled = rankProfile.compile(queryProfiles, importedModels);
        constants = compiled.constants().values();
        onnxModels = compiled.onnxModels().values();
        var deriver = new Deriver(compiled, attributeFields, deployProperties, queryProfiles);
        compressedProperties = compress(deriver.derive(largeExpressions));
        this.featureNormalizers = compiled.getFeatureNormalizers();
    }

    public Collection<RankProfile.Constant> constants() { return constants; }
    public Collection<OnnxModel> onnxModels() { return onnxModels; }

    private Compressor.Compression compress(List<Pair<String, String>> properties) {
        StringBuilder b = new StringBuilder();
        for (Pair<String, String> property : properties)
            b.append(property.getFirst()).append(keyEndMarker).append(property.getSecond()).append(valueEndMarker);
        return compressor.compress(b.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<Pair<String, String>> decompress(Compressor.Compression compression) {
        String propertiesString = new String(compressor.decompress(compression), StandardCharsets.UTF_8);
        if (propertiesString.isEmpty()) return List.of();

        List<Pair<String, String>> properties = new ArrayList<>();
        for (int pos = 0; pos < propertiesString.length();) {
            int keyEndPos = propertiesString.indexOf(keyEndMarker, pos);
            String key = propertiesString.substring(pos, keyEndPos);
            pos = keyEndPos + keyEndMarker.length();
            int valueEndPos = propertiesString.indexOf(valueEndMarker, pos);
            String value = propertiesString.substring(pos, valueEndPos);
            pos = valueEndPos + valueEndMarker.length();
            properties.add(new Pair<>(key, value));
        }
        return List.copyOf(properties);
    }

    public String getName() { return name; }

    private void getRankProperties(RankProfilesConfig.Rankprofile.Builder b) {
        RankProfilesConfig.Rankprofile.Fef.Builder fefB = new RankProfilesConfig.Rankprofile.Fef.Builder();
        for (Pair<String, String> p : decompress(compressedProperties))
            fefB.property(new RankProfilesConfig.Rankprofile.Fef.Property.Builder().name(p.getFirst()).value(p.getSecond()));
        b.fef(fefB);
    }

    private void buildNormalizers(RankProfilesConfig.Rankprofile.Builder b) {
        for (var normalizer : featureNormalizers.values()) {
            var nBuilder = new RankProfilesConfig.Rankprofile.Normalizer.Builder();
            nBuilder.name(normalizer.name());
            nBuilder.input(normalizer.input());
            var algo = RankProfilesConfig.Rankprofile.Normalizer.Algo.Enum.valueOf(normalizer.algo());
            nBuilder.algo(algo);
            nBuilder.kparam(normalizer.kparam());
            b.normalizer(nBuilder);
        }
     }

    /**
     * Returns the properties of this as an unmodifiable list.
     * Note: This method is expensive.
     */
    public List<Pair<String, String>> configProperties() { return decompress(compressedProperties); }

    public RankProfilesConfig.Rankprofile.Builder getConfig() {
        RankProfilesConfig.Rankprofile.Builder b = new RankProfilesConfig.Rankprofile.Builder().name(getName());
        getRankProperties(b);
        buildNormalizers(b);
        return b;
    }

    @Override
    public String toString() {
        return " rank profile " + name;
    }

    private static class Deriver {

        private final Map<String, FieldRankSettings> fieldRankSettings = new java.util.LinkedHashMap<>();
        private final Set<ReferenceNode> summaryFeatures;
        private final Set<ReferenceNode> matchFeatures;
        private final Set<ReferenceNode> hiddenMatchFeatures;
        private final Set<ReferenceNode> rankFeatures;
        private final Map<String, String> featureRenames = new java.util.LinkedHashMap<>();
        private final List<RankProfile.RankProperty> rankProperties;

        /**
         * Rank properties for weight settings to make these available to feature executors
         */
        private final List<RankProfile.RankProperty> boostAndWeightRankProperties = new ArrayList<>();

        private final boolean ignoreDefaultRankFeatures;
        private final RankProfile.MatchPhaseSettings matchPhaseSettings;
        private final RankProfile.DiversitySettings diversitySettings;
        private final Optional<Integer> rerankCount;
        private final int keepRankCount;
        private final int numThreadsPerSearch;
        private final int minHitsPerThread;
        private final int numSearchPartitions;
        private final double termwiseLimit;
        private final OptionalDouble postFilterThreshold;
        private final OptionalDouble approximateThreshold;
        private final OptionalDouble filterFirstThreshold;
        private final OptionalDouble filterFirstExploration;
        private final OptionalDouble explorationSlack;
        private final Boolean prefetchTensors;
        private final OptionalDouble targetHitsMaxAdjustmentFactor;
        private final OptionalDouble weakandStopwordLimit;
        private final Boolean weakandAllowDropAll;
        private final OptionalDouble weakandAdjustTarget;
        private final OptionalDouble filterThreshold;
        private final double rankScoreDropLimit;
        private final double secondPhaseRankScoreDropLimit;
        private final double globalPhaseRankScoreDropLimit;
        private final boolean sortBlueprintsByCost;

        /**
         * The rank type definitions used to derive settings for the native rank features
         */
        private final NativeRankTypeDefinitionSet nativeRankTypeDefinitions = new NativeRankTypeDefinitionSet("default");
        private final Map<String, String> attributeTypes;
        private final Map<Reference, RankProfile.Input> inputs;
        private final Set<String> filterFields = new java.util.LinkedHashSet<>();
        private final Map<String, Double> explicitFieldRankFilterThresholds = new LinkedHashMap<>();
        private final Map<String, ElementGap> activeElementGapsPerField = new LinkedHashMap<>();
        private final String rankprofileName;

        private RankingExpression firstPhaseRanking;
        private RankingExpression secondPhaseRanking;
        private RankingExpression globalPhaseRanking;
        private final int globalPhaseRerankCount;
        private final SerializationContext functionSerializationContext;

        /**
         * Creates a raw rank profile from the given rank profile
         */
        Deriver(RankProfile compiled,
                AttributeFields attributeFields,
                ModelContext.Properties deployProperties,
                QueryProfileRegistry queryProfiles) {
            rankprofileName = compiled.name();
            attributeTypes = compiled.getAttributeTypes();
            inputs = compiled.inputs();
            firstPhaseRanking = compiled.getFirstPhaseRanking();
            secondPhaseRanking = compiled.getSecondPhaseRanking();
            globalPhaseRanking = compiled.getGlobalPhaseRanking();
            summaryFeatures = new LinkedHashSet<>(compiled.getSummaryFeatures());
            matchFeatures = new LinkedHashSet<>(compiled.getMatchFeatures());
            hiddenMatchFeatures = compiled.getHiddenMatchFeatures();
            matchFeatures.addAll(hiddenMatchFeatures);
            rankFeatures = compiled.getRankFeatures();
            rerankCount = compiled.getRerankCount();
            globalPhaseRerankCount = compiled.getGlobalPhaseRerankCount();
            matchPhaseSettings = compiled.getMatchPhase();
            diversitySettings = compiled.getDiversity();
            numThreadsPerSearch = compiled.getNumThreadsPerSearch();
            minHitsPerThread = compiled.getMinHitsPerThread();
            numSearchPartitions = compiled.getNumSearchPartitions();
            termwiseLimit = compiled.getTermwiseLimit().orElse(1.0);
            sortBlueprintsByCost = deployProperties.featureFlags().sortBlueprintsByCost();
            postFilterThreshold = compiled.getPostFilterThreshold();
            approximateThreshold = compiled.getApproximateThreshold();
            filterFirstThreshold = compiled.getFilterFirstThreshold();
            filterFirstExploration = compiled.getFilterFirstExploration();
            explorationSlack = compiled.getExplorationSlack();
            prefetchTensors = compiled.getPrefetchTensors();
            targetHitsMaxAdjustmentFactor = compiled.getTargetHitsMaxAdjustmentFactor();
            weakandStopwordLimit = compiled.getWeakandStopwordLimit();
            weakandAdjustTarget = compiled.getWeakandAdjustTarget();
            weakandAllowDropAll = compiled.getWeakandAllowDropAll();
            filterThreshold = compiled.getFilterThreshold();
            keepRankCount = compiled.getKeepRankCount();
            rankScoreDropLimit = compiled.getRankScoreDropLimit();
            secondPhaseRankScoreDropLimit = compiled.getSecondPhaseRankScoreDropLimit();
            globalPhaseRankScoreDropLimit = compiled.getGlobalPhaseRankScoreDropLimit();
            ignoreDefaultRankFeatures = compiled.getIgnoreDefaultRankFeatures();
            rankProperties = new ArrayList<>(compiled.getRankProperties());

            Map<String, RankProfile.RankingExpressionFunction> functions = compiled.getFunctions();
            List<ExpressionFunction> functionExpressions = functions.values().stream().map(RankProfile.RankingExpressionFunction::function).toList();
            Map<String, String> functionProperties = new LinkedHashMap<>();
            var typeContext = compiled.typeContext(queryProfiles);
            this.functionSerializationContext = new SerializationContext(functionExpressions, Map.of(), typeContext);
            if (firstPhaseRanking != null) {
                functionProperties.putAll(firstPhaseRanking.getRankProperties(functionSerializationContext));
            }
            if (secondPhaseRanking != null) {
                functionProperties.putAll(secondPhaseRanking.getRankProperties(functionSerializationContext));
            }
            if (globalPhaseRanking != null) {
                functionProperties.putAll(globalPhaseRanking.getRankProperties(functionSerializationContext));
            }
            deriveFeatureDeclarations(matchFeatures, typeContext, functionProperties);
            deriveFeatureDeclarations(summaryFeatures, typeContext, functionProperties);
            derivePropertiesAndFeaturesFromFunctions(functions, functionProperties, functionSerializationContext);
            deriveOnnxModelFunctionsAndFeatures(compiled);

            deriveRankTypeSetting(compiled, attributeFields);
            deriveFilterFields(compiled);
            deriveElementGaps(compiled);
            deriveWeightProperties(compiled);
        }

        private void deriveFeatureDeclarations(Collection<ReferenceNode> features,
                                               TypeContext typeContext,
                                               Map<String, String> functionProperties)
        {
            for (ReferenceNode feature : features) {
                var tt = feature.type(typeContext);
                if (tt != null && tt.rank() > 0) {
                    String k = "vespa.type.feature." + feature.getName() + feature.getArguments();
                    functionProperties.put(k, tt.toString());
                }
            }
        }

        private void deriveFilterFields(RankProfile rp) {
            filterFields.addAll(rp.allFilterFields());
            explicitFieldRankFilterThresholds.putAll(rp.explicitFieldRankFilterThresholds());
        }

        private void deriveElementGaps(RankProfile rp) {
            activeElementGapsPerField.putAll(rp.getFieldRankElementGaps());
        }

        private void derivePropertiesAndFeaturesFromFunctions(Map<String, RankProfile.RankingExpressionFunction> functions,
                                                              Map<String, String> functionProperties,
                                                              SerializationContext functionContext) {
            replaceFunctionFeatures(summaryFeatures, functionContext);
            replaceFunctionFeatures(matchFeatures, functionContext);

            // First phase, second phase and summary features should add all required functions to the context.
            // However, we need to add any functions not referenced in those anyway for model-evaluation.
            deriveFunctionProperties(functions, functionProperties, functionContext);

            for (Map.Entry<String, String> e : functionProperties.entrySet()) {
                rankProperties.add(new RankProfile.RankProperty(e.getKey(), e.getValue()));
            }
        }

        private void deriveFunctionProperties(Map<String, RankProfile.RankingExpressionFunction> functions,
                                              Map<String, String> functionProperties,
                                              SerializationContext context) {
            for (Map.Entry<String, RankProfile.RankingExpressionFunction> e : functions.entrySet()) {
                String propertyName = RankingExpression.propertyName(e.getKey());
                if (! context.serializedFunctions().containsKey(propertyName)) {
                    String expressionString = e.getValue().function().getBody().getRoot().toString(context).toString();
                    context.addFunctionSerialization(propertyName, expressionString);

                    e.getValue().function().argumentTypes().entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .forEach(argumentType -> context.addArgumentTypeSerialization(e.getKey(), argumentType.getKey(), argumentType.getValue()));
                }
                e.getValue().function().returnType().ifPresent(t -> context.addFunctionTypeSerialization(e.getKey(), t));

                // else if (e.getValue().function().arguments().isEmpty()) TODO: Enable this check when we resolve all types
                //     throw new IllegalStateException("Type of function '" + e.getKey() + "' is not resolved");
            }
            functionProperties.putAll(context.serializedFunctions());
        }

        private void replaceFunctionFeatures(Set<ReferenceNode> features, SerializationContext context) {
            if (features == null) return;
            Set<ReferenceNode> functionFeatures = new LinkedHashSet<>();
            for (Iterator<ReferenceNode> i = features.iterator(); i.hasNext(); ) {
                ReferenceNode referenceNode = i.next();
                // Is the feature a function?
                ExpressionFunction function = context.getFunction(referenceNode.getName());
                if (function != null) {
                    if (referenceNode.getOutput() != null) {
                        throw new IllegalArgumentException("function " + referenceNode.getName() +
                                                           " cannot provide output " + referenceNode.getOutput() +
                                                           " demanded by feature " + referenceNode);
                    }
                    int needArgs = function.arguments().size();
                    var useArgs = referenceNode.getArguments();
                    if (needArgs != useArgs.size()) {
                        throw new IllegalArgumentException("function " + referenceNode.getName() +
                                                           " needs " + needArgs +
                                                           " arguments but gets " + useArgs.size() +
                                                           " from feature " + referenceNode);
                    }
                    var instance = function.expand(context, useArgs.expressions(), new java.util.ArrayDeque<>());
                    String propertyName = RankingExpression.propertyName(instance.getName());
                    String expressionString = instance.getExpressionString();
                    context.addFunctionSerialization(propertyName, expressionString);
                    function.returnType().ifPresent(t -> context.addFunctionTypeSerialization(instance.getName(), t));
                    String backendReference = wrapInRankingExpression(instance.getName());
                    var backendReferenceNode = new ReferenceNode(backendReference, List.of(), null);
                    // tell backend to map back to the name the user expects:
                    featureRenames.put(backendReference, referenceNode.toString());
                    functionFeatures.add(backendReferenceNode);
                    i.remove(); // Will add the expanded one in next block
                }
            }
            // Then, replace the features that were functions
            for (ReferenceNode mappedFun : functionFeatures) {
                features.add(mappedFun);
            }
        }

        private void deriveWeightProperties(RankProfile rankProfile) {

            for (RankProfile.RankSetting setting : rankProfile.rankSettings()) {
                if (setting.getType() != RankProfile.RankSetting.Type.WEIGHT) continue;
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
                if (setting.getType() != RankProfile.RankSetting.Type.RANKTYPE) continue;

                deriveNativeRankTypeSetting(setting.getFieldName(), (RankType) setting.getValue(), attributeFields,
                                            hasDefaultRankTypeSetting(rankProfile, setting.getFieldName()));
            }
        }

        private void deriveNativeRankTypeSetting(String fieldName, RankType rankType, AttributeFields attributeFields,
                                                 boolean isDefaultSetting) {
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
        public List<Pair<String, String>> derive(LargeRankingExpressions largeRankingExpressions) {
            List<Pair<String, String>>  properties = new ArrayList<>();
            for (RankProfile.RankProperty property : rankProperties) {
                if (RankingExpression.propertyName(RankProfile.FIRST_PHASE).equals(property.getName())) {
                    // Could have been set by function expansion. Set expressions, then skip this property.
                    try {
                        firstPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse first phase expression", e);
                    }
                }
                else if (RankingExpression.propertyName(RankProfile.SECOND_PHASE).equals(property.getName())) {
                    try {
                        secondPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse second phase expression", e);
                    }
                }
                else if (RankingExpression.propertyName(RankProfile.GLOBAL_PHASE).equals(property.getName())) {
                    try {
                        globalPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse global-phase expression", e);
                    }
                }
                else {
                    properties.add(new Pair<>(property.getName(), property.getValue()));
                }
            }
            properties.addAll(deriveRankingPhaseRankProperties(firstPhaseRanking, RankProfile.FIRST_PHASE));
            properties.addAll(deriveRankingPhaseRankProperties(secondPhaseRanking, RankProfile.SECOND_PHASE));
            properties.addAll(deriveRankingPhaseRankProperties(globalPhaseRanking, RankProfile.GLOBAL_PHASE));
            for (FieldRankSettings settings : fieldRankSettings.values()) {
                properties.addAll(settings.deriveRankProperties());
            }
            for (RankProfile.RankProperty property : boostAndWeightRankProperties) {
                properties.add(new Pair<>(property.getName(), property.getValue()));
            }
            for (ReferenceNode feature : summaryFeatures) {
                properties.add(new Pair<>("vespa.summary.feature", feature.toString()));
            }
            for (ReferenceNode feature : matchFeatures) {
                properties.add(new Pair<>("vespa.match.feature", feature.toString()));
            }
            for (ReferenceNode feature : hiddenMatchFeatures) {
                properties.add(new Pair<>("vespa.hidden.matchfeature", feature.toString()));
            }
            for (ReferenceNode feature : rankFeatures) {
                properties.add(new Pair<>("vespa.dump.feature", feature.toString()));
            }
            for (var entry : featureRenames.entrySet()) {
                properties.add(new Pair<>("vespa.feature.rename", entry.getKey()));
                properties.add(new Pair<>("vespa.feature.rename", entry.getValue()));
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
            if (sortBlueprintsByCost) {
                properties.add(new Pair<>("vespa.matching.sort_blueprints_by_cost", String.valueOf(sortBlueprintsByCost)));
            }
            if (postFilterThreshold.isPresent()) {
                properties.add(new Pair<>("vespa.matching.global_filter.upper_limit", String.valueOf(postFilterThreshold.getAsDouble())));
            }
            if (approximateThreshold.isPresent()) {
                properties.add(new Pair<>("vespa.matching.global_filter.lower_limit", String.valueOf(approximateThreshold.getAsDouble())));
            }
            if (filterFirstThreshold.isPresent()) {
                properties.add(new Pair<>("vespa.matching.nns.filter_first_upper_limit", String.valueOf(filterFirstThreshold.getAsDouble())));
            }
            if (filterFirstExploration.isPresent()) {
                properties.add(new Pair<>("vespa.matching.nns.filter_first_exploration", String.valueOf(filterFirstExploration.getAsDouble())));
            }
            if (explorationSlack.isPresent()) {
                properties.add(new Pair<>("vespa.matching.nns.exploration_slack", String.valueOf(explorationSlack.getAsDouble())));
            }
            if (prefetchTensors != null) {
                properties.add(new Pair<>("vespa.matching.nns.prefetch_tensors", String.valueOf(prefetchTensors)));
            }
            if (targetHitsMaxAdjustmentFactor.isPresent()) {
                properties.add(new Pair<>("vespa.matching.nns.target_hits_max_adjustment_factor", String.valueOf(targetHitsMaxAdjustmentFactor.getAsDouble())));
            }
            if (weakandStopwordLimit.isPresent()) {
                properties.add(new Pair<>("vespa.matching.weakand.stop_word_drop_limit", String.valueOf(weakandStopwordLimit.getAsDouble())));
            }
            if (weakandAllowDropAll != null) {
                properties.add(new Pair<>("vespa.matching.weakand.allow_drop_all", String.valueOf(weakandAllowDropAll)));
            }
            if (weakandAdjustTarget.isPresent()) {
                properties.add(new Pair<>("vespa.matching.weakand.stop_word_adjust_limit", String.valueOf(weakandAdjustTarget.getAsDouble())));
            }
            if (filterThreshold.isPresent()) {
                properties.add(new Pair<>("vespa.matching.filter_threshold", String.valueOf(filterThreshold.getAsDouble())));
            }
            for (var fieldAndThreshold : explicitFieldRankFilterThresholds.entrySet()) {
                properties.add(new Pair<>(Text.format("vespa.matching.filter_threshold.%s", fieldAndThreshold.getKey()), String.valueOf(fieldAndThreshold.getValue())));
            }
            for (var fieldAndElementGap : activeElementGapsPerField.entrySet()) {
                properties.add(new Pair<>(Text.format("vespa.matching.element_gap.%s", fieldAndElementGap.getKey()), fieldAndElementGap.getValue().toString()));
            }
            if (matchPhaseSettings != null) {
                properties.add(new Pair<>("vespa.matchphase.degradation.attribute", matchPhaseSettings.getAttribute()));
                properties.add(new Pair<>("vespa.matchphase.degradation.ascendingorder", matchPhaseSettings.getAscending() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.maxhits", matchPhaseSettings.getMaxHits() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.maxfiltercoverage", matchPhaseSettings.getMaxFilterCoverage() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.samplepercentage", matchPhaseSettings.getEvaluationPoint() + ""));
                properties.add(new Pair<>("vespa.matchphase.degradation.postfiltermultiplier", matchPhaseSettings.getPrePostFilterTippingPoint() + ""));
            }
            if (diversitySettings != null) {
                properties.add(new Pair<>("vespa.matchphase.diversity.attribute", diversitySettings.getAttribute()));
                properties.add(new Pair<>("vespa.matchphase.diversity.mingroups", String.valueOf(diversitySettings.getMinGroups())));
                properties.add(new Pair<>("vespa.matchphase.diversity.cutoff.factor", String.valueOf(diversitySettings.getCutoffFactor())));
                properties.add(new Pair<>("vespa.matchphase.diversity.cutoff.strategy", String.valueOf(diversitySettings.getCutoffStrategy())));
            }
            rerankCount.ifPresent(count -> properties.add(new Pair<>("vespa.hitcollector.heapsize", count + "")));
            if (keepRankCount > -1) {
                properties.add(new Pair<>("vespa.hitcollector.arraysize", keepRankCount + ""));
            }
            if (globalPhaseRerankCount > -1) {
                properties.add(new Pair<>("vespa.globalphase.rerankcount", globalPhaseRerankCount + ""));
            }
            if (globalPhaseRankScoreDropLimit > -Double.MAX_VALUE) {
                properties.add(new Pair<>("vespa.globalphase.rankscoredroplimit", globalPhaseRankScoreDropLimit + ""));
            }
            if (rankScoreDropLimit > -Double.MAX_VALUE) {
                properties.add(new Pair<>("vespa.hitcollector.rankscoredroplimit", rankScoreDropLimit + ""));
            }
            if (secondPhaseRankScoreDropLimit > -Double.MAX_VALUE) {
                properties.add(new Pair<>("vespa.hitcollector.secondphase.rankscoredroplimit", secondPhaseRankScoreDropLimit + ""));
            }
            if (ignoreDefaultRankFeatures) {
                properties.add(new Pair<>("vespa.dump.ignoredefaultfeatures", String.valueOf(true)));
            }
            for (String fieldName : filterFields) {
                properties.add(new Pair<>("vespa.isfilterfield." + fieldName, String.valueOf(true)));
            }
            for (Map.Entry<String, String> attributeType : attributeTypes.entrySet()) {
                properties.add(new Pair<>("vespa.type.attribute." + attributeType.getKey(), attributeType.getValue()));
            }

            for (var input : inputs.values()) {
                if (FeatureNames.isQueryFeature(input.name())) {
                    if (input.type().tensorType().rank() > 0) // Proton does not like representing the double type as a rank 0 tensor
                        properties.add(new Pair<>("vespa.type.query." + input.name().arguments().expressions().get(0),
                                                  input.type().toString()));
                    if (input.defaultValue().isPresent()) {
                        properties.add(new Pair<>(input.name().toString(),
                                                  input.type().tensorType().rank() == 0 ?
                                                  String.valueOf(input.defaultValue().get().asDouble()) :
                                                  input.defaultValue().get().toString(true, false)));
                    }
                }
            }
            if (properties.size() >= 1000000) throw new IllegalArgumentException("Too many rank properties");
            distributeLargeExpressionsAsFiles(properties, largeRankingExpressions);
            return properties;
        }

        private void distributeLargeExpressionsAsFiles(List<Pair<String, String>> properties, LargeRankingExpressions largeRankingExpressions) {
            for (ListIterator<Pair<String, String>> iter = properties.listIterator(); iter.hasNext();) {
                Pair<String, String> property = iter.next();
                String expression = property.getSecond();
                if (expression.length() > largeRankingExpressions.limit()) {
                    String propertyName = property.getFirst();
                    String functionName = RankingExpression.extractScriptName(propertyName);
                    if (functionName != null) {
                        String mangledName = rankprofileName + "." + functionName;
                        largeRankingExpressions.add(new RankingExpressionBody(mangledName, ByteBuffer.wrap(expression.getBytes(StandardCharsets.UTF_8))));
                        iter.set(new Pair<>(RankingExpression.propertyExpressionName(functionName), mangledName));
                    }
                }
            }
        }

        private List<Pair<String, String>> deriveRankingPhaseRankProperties(RankingExpression expression, String phase) {
            List<Pair<String, String>> properties = new ArrayList<>();
            if (expression == null) return properties;

            String name = expression.getName();
            if ("".equals(name))
                name = phase;

            String expressionAsString = expression.getRoot().toString(functionSerializationContext).toString();
            if (expression.getRoot() instanceof ReferenceNode) {
                properties.add(new Pair<>("vespa.rank." + phase, expressionAsString));
            } else {
                properties.add(new Pair<>("vespa.rank." + phase, wrapInRankingExpression(name)));
                properties.add(new Pair<>(RankingExpression.propertyName(name), expressionAsString));
            }
            return properties;
        }

        private void deriveOnnxModelFunctionsAndFeatures(RankProfile rankProfile) {
            if (rankProfile.schema() == null) return;
            if (rankProfile.onnxModels().isEmpty()) return;
            replaceOnnxFunctionInputs(rankProfile);
            replaceImplicitOnnxConfigFeatures(summaryFeatures, rankProfile);
            replaceImplicitOnnxConfigFeatures(matchFeatures, rankProfile);
        }

        private void replaceOnnxFunctionInputs(RankProfile rankProfile) {
            Set<String> functionNames = rankProfile.getFunctions().keySet();
            if (functionNames.isEmpty()) return;
            for (OnnxModel onnxModel: rankProfile.onnxModels().values()) {
                for (Map.Entry<String, String> mapping : onnxModel.getInputMap().entrySet()) {
                    String source = mapping.getValue();
                    if (functionNames.contains(source)) {
                        onnxModel.addInputNameMapping(mapping.getKey(), wrapInRankingExpression(source));
                    }
                }
            }
        }

        private void replaceImplicitOnnxConfigFeatures(Set<ReferenceNode> features, RankProfile rankProfile) {
            if (features == null || features.isEmpty()) return;
            Set<ReferenceNode> replacedFeatures = new HashSet<>();
            for (Iterator<ReferenceNode> i = features.iterator(); i.hasNext(); ) {
                ReferenceNode referenceNode = i.next();
                ReferenceNode replacedNode = (ReferenceNode) OnnxModelTransformer.transformFeature(referenceNode, rankProfile);
                if (referenceNode != replacedNode) {
                    replacedFeatures.add(replacedNode);
                    i.remove();
                }
            }
            features.addAll(replacedFeatures);
        }

    }

}
