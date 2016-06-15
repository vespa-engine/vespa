// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.document.RankType;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.searchlib.rankingexpression.rule.SerializationContext;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import java.util.*;

/**
 * A rank profile derived from a search definition, containing exactly the features available natively in the server
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon S Bratseth</a>
 */
public class RawRankProfile implements RankProfilesConfig.Producer {

    private String name;

    public String getName() {
        return name;
    }

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
        for (Map.Entry<String, Object> e : configProperties.entrySet()) {
            String key = e.getKey().replaceFirst(".part\\d+$", "");
            String val = e.getValue().toString();
            fefB.property(new RankProfilesConfig.Rankprofile.Fef.Property.Builder().name(key).value(val));
        }
        b.fef(fefB);
    }

    // TODO: These are to expose coupling between the strings used here and elsewhere
    public final static String summaryFeatureFefPropertyPrefix = "vespa.summary.feature";
    public final static String rankFeatureFefPropertyPrefix = "vespa.dump.feature";

    /**
     * Returns an immutable view of the config properties this returns
     */
    public Map<String, Object> configProperties() {
        return Collections.unmodifiableMap(configProperties);
    }

    private final Map<String, Object> configProperties;

    /**
     * Creates a raw rank profile from the given rank profile
     */
    public RawRankProfile(RankProfile rankProfile, AttributeFields attributeFields) {
        this.name = rankProfile.getName();
        configProperties = new Deriver(rankProfile, attributeFields).derive();
    }

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
        private int numSearchPartitions = -1;
        private double termwiseLimit = 1.0;
        private double rankScoreDropLimit = -Double.MAX_VALUE;

        /**
         * The rank type definitions used to derive settings for the native rank features
         */
        private NativeRankTypeDefinitionSet nativeRankTypeDefinitions = new NativeRankTypeDefinitionSet("default");

        private RankProfile rankProfile;

        private Set<String> filterFields = new java.util.LinkedHashSet<>();

        /**
         * Creates a raw rank profile from the given rank profile
         */
        public Deriver(RankProfile rankProfile, AttributeFields attributeFields) {
            this.rankProfile = rankProfile.compile();
            deriveRankingFeatures(this.rankProfile);
            deriveRankTypeSetting(this.rankProfile, attributeFields);
            deriveFilterFields(this.rankProfile);
            deriveWeightProperties(this.rankProfile);
        }

        private void deriveFilterFields(RankProfile rp) {
            filterFields.addAll(rp.allFilterFields());
        }

        public void deriveRankingFeatures(RankProfile rankProfile) {
            firstPhaseRanking = rankProfile.getFirstPhaseRanking();
            secondPhaseRanking = rankProfile.getSecondPhaseRanking();
            summaryFeatures = new LinkedHashSet<>(rankProfile.getSummaryFeatures());
            rankFeatures = rankProfile.getRankFeatures();
            rerankCount = rankProfile.getRerankCount();
            matchPhaseSettings = rankProfile.getMatchPhaseSettings();
            numThreadsPerSearch = rankProfile.getNumThreadsPerSearch();
            numSearchPartitions = rankProfile.getNumSearchPartitions();
            termwiseLimit = rankProfile.getTermwiseLimit();
            keepRankCount = rankProfile.getKeepRankCount();
            rankScoreDropLimit = rankProfile.getRankScoreDropLimit();
            ignoreDefaultRankFeatures = rankProfile.getIgnoreDefaultRankFeatures();
            rankProperties = new ArrayList<>(rankProfile.getRankProperties());
            derivePropertiesAndSummaryFeaturesFromMacros(rankProfile.getMacros());
        }

        private void derivePropertiesAndSummaryFeaturesFromMacros(Map<String, RankProfile.Macro> macros) {
            if (macros.isEmpty()) return;
            Map<String, ExpressionFunction> expressionMacros = new LinkedHashMap<>();
            for (Map.Entry<String, RankProfile.Macro> macro : macros.entrySet()) {
                expressionMacros.put(macro.getKey(), macro.getValue().toExpressionMacro());
            }

            Map<String, String> macroProperties = new LinkedHashMap<>();
            macroProperties.putAll(deriveMacroProperties(expressionMacros));
            if (firstPhaseRanking != null) {
                macroProperties.putAll(firstPhaseRanking.getRankProperties(new ArrayList<>(expressionMacros.values())));
            }
            if (secondPhaseRanking != null) {
                macroProperties.putAll(secondPhaseRanking.getRankProperties(new ArrayList<>(expressionMacros.values())));
            }
            for (Map.Entry<String, String> e : macroProperties.entrySet()) {
                rankProperties.add(new RankProfile.RankProperty(e.getKey(), e.getValue()));
            }
            SerializationContext context = new SerializationContext(expressionMacros.values(), null, macroProperties);
            replaceMacroSummaryFeatures(context);
        }

        private Map<String, String> deriveMacroProperties(Map<String, ExpressionFunction> eMacros) {
            SerializationContext context = new SerializationContext(eMacros);
            for (Map.Entry<String, ExpressionFunction> e : eMacros.entrySet()) {
                String script = e.getValue().getBody().getRoot().toString(context, null, null);
                context.addFunctionSerialization(RankingExpression.propertyName(e.getKey()), script);
            }
            return context.serializedFunctions();
        }

        private void replaceMacroSummaryFeatures(SerializationContext context) {
            if (summaryFeatures == null) return;
            Map<String, ReferenceNode> macroSummaryFeatures = new LinkedHashMap<>();
            for (Iterator<ReferenceNode> i = summaryFeatures.iterator(); i.hasNext(); ) {
                ReferenceNode referenceNode = i.next();
                // Is the feature a macro?
                if (context.getFunction(referenceNode.getName()) != null) {
                    context.addFunctionSerialization(RankingExpression.propertyName(referenceNode.getName()),
                            referenceNode.toString(context, null, null));
                    ReferenceNode newReferenceNode = new ReferenceNode("rankingExpression(" + referenceNode.getName() + ")", referenceNode.getArguments().expressions(), referenceNode.getOutput());
                    macroSummaryFeatures.put(referenceNode.getName(), newReferenceNode);
                    i.remove(); // Will add the expanded one in next block
                }
            }
            // Then, replace the summary features that were macros
            for (Map.Entry<String, ReferenceNode> e : macroSummaryFeatures.entrySet()) {
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

        public void deriveNativeRankTypeSetting(String fieldName, RankType rankType, AttributeFields attributeFields, boolean isDefaultSetting) {
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

        public FieldRankSettings deriveFieldRankSettings(String fieldName) {
            FieldRankSettings settings = fieldRankSettings.get(fieldName);
            if (settings == null) {
                settings = new FieldRankSettings(fieldName);
                fieldRankSettings.put(fieldName, settings);
            }
            return settings;
        }

        /**
         * Derives the properties this produces. Equal keys are suffixed with .part0 etc, remove when exporting to file
         *
         * @return map of the derived properties
         */
        public Map<String, Object> derive() {
            Map<String, Object> props = new LinkedHashMap<>();
            int i = 0;
            for (RankProfile.RankProperty property : rankProperties) {
                if ("rankingExpression(firstphase).rankingScript".equals(property.getName())) {
                    // Could have been set by macro expansion. Set expressions, then skip this property.
                    try {
                        firstPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse second phase expression", e);
                    }
                    continue;
                }
                if ("rankingExpression(secondphase).rankingScript".equals(property.getName())) {
                    try {
                        secondPhaseRanking = new RankingExpression(property.getValue());
                    } catch (ParseException e) {
                        throw new IllegalArgumentException("Could not parse second phase expression", e);
                    }
                    continue;
                }
                props.put(property.getName() + ".part" + i, property.getValue());
                i++;
            }
            props.putAll(deriveRankingPhaseRankProperties(firstPhaseRanking, "firstphase"));
            props.putAll(deriveRankingPhaseRankProperties(secondPhaseRanking, "secondphase"));
            for (FieldRankSettings settings : fieldRankSettings.values()) {
                props.putAll(settings.deriveRankProperties(i));
            }
            i = 0;
            for (RankProfile.RankProperty property : boostAndWeightRankProperties) {
                props.put(property.getName() + ".part" + i, property.getValue());
                i++;
            }
            i = 0;
            for (ReferenceNode feature : summaryFeatures) {
                props.put(summaryFeatureFefPropertyPrefix + ".part" + i, feature.toString());
                i++;
            }
            i = 0;
            for (ReferenceNode feature : rankFeatures) {
                props.put(rankFeatureFefPropertyPrefix + ".part" + i, feature.toString());
                i++;
            }
            if (numThreadsPerSearch > 0) {
                props.put("vespa.matching.numthreadspersearch", numThreadsPerSearch + "");
            }
            if (numSearchPartitions >= 0) {
                props.put("vespa.matching.numsearchpartitions", numSearchPartitions + "");
            }
            if (termwiseLimit < 1.0) {
                props.put("vespa.matching.termwise_limit", termwiseLimit + "");
            }
            if (matchPhaseSettings != null) {
                props.put("vespa.matchphase.degradation.attribute", matchPhaseSettings.getAttribute());
                props.put("vespa.matchphase.degradation.ascendingorder", matchPhaseSettings.getAscending() + "");
                props.put("vespa.matchphase.degradation.maxhits", matchPhaseSettings.getMaxHits() + "");
                props.put("vespa.matchphase.degradation.maxfiltercoverage", matchPhaseSettings.getMaxFilterCoverage() + "");
                props.put("vespa.matchphase.degradation.samplepercentage", matchPhaseSettings.getEvaluationPoint() + "");
                props.put("vespa.matchphase.degradation.postfiltermultiplier", matchPhaseSettings.getPrePostFilterTippingPoint() + "");
                RankProfile.DiversitySettings diversitySettings = rankProfile.getMatchPhaseSettings().getDiversity();
                if (diversitySettings != null) {
                    props.put("vespa.matchphase.diversity.attribute", diversitySettings.getAttribute());
                    props.put("vespa.matchphase.diversity.mingroups", diversitySettings.getMinGroups());
                    props.put("vespa.matchphase.diversity.cutoff.factor", diversitySettings.getCutoffFactor());
                    props.put("vespa.matchphase.diversity.cutoff.strategy", diversitySettings.getCutoffStrategy());
                }
            }
            if (rerankCount > -1) {
                props.put("vespa.hitcollector.heapsize", rerankCount + "");
            }
            if (keepRankCount > -1) {
                props.put("vespa.hitcollector.arraysize", keepRankCount + "");
            }
            if (rankScoreDropLimit > -Double.MAX_VALUE) {
                props.put("vespa.hitcollector.rankscoredroplimit", rankScoreDropLimit + "");
            }
            if (ignoreDefaultRankFeatures) {
                props.put("vespa.dump.ignoredefaultfeatures", true);
            }
            Iterator filterFieldsIterator = filterFields.iterator();
            while (filterFieldsIterator.hasNext()) {
                String fieldName = (String) filterFieldsIterator.next();
                props.put("vespa.isfilterfield." + fieldName + ".part42", true);
            }
            for (Map.Entry<String, String> attributeType : rankProfile.getAttributeTypes().entrySet()) {
                props.put("vespa.type.attribute." + attributeType.getKey(), attributeType.getValue());
            }
            for (Map.Entry<String, String> queryFeatureType : rankProfile.getQueryFeatureTypes().entrySet()) {
                props.put("vespa.type.query." + queryFeatureType.getKey(), queryFeatureType.getValue());
            }
            if (props.size() >= 1000000) throw new RuntimeException("Too many rank properties");
            return props;
        }

        private Map<String, String> deriveRankingPhaseRankProperties(RankingExpression expression, String phase) {
            Map<String, String> ret = new LinkedHashMap<>();
            if (expression == null) {
                return ret;
            }
            String name = expression.getName();
            if ("".equals(name)) {
                name = phase;
            }
            if (expression.getRoot() instanceof ReferenceNode) {
                ret.put("vespa.rank." + phase, expression.getRoot().toString());
            } else {
                ret.put("vespa.rank." + phase, "rankingExpression(" + name + ")");
                ret.put("rankingExpression(" + name + ").rankingScript", expression.getRoot().toString());
            }
            return ret;
        }

    }

}
