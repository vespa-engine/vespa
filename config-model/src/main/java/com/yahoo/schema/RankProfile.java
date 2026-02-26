// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.path.Path;
import com.yahoo.searchlib.ranking.features.FeatureNames;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.ElementGap;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.expressiontransforms.ExpressionTransforms;
import com.yahoo.schema.expressiontransforms.RankProfileTransformContext;
import com.yahoo.schema.expressiontransforms.InputRecorder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.search.schema.RankProfile.InputType;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents a rank profile - a named set of ranking settings
 *
 * @author bratseth
 */
public class RankProfile implements Cloneable {

    public final static String FIRST_PHASE = "firstphase";
    public final static String SECOND_PHASE = "secondphase";
    public final static String GLOBAL_PHASE = "globalphase";

    /** The schema-unique name of this rank profile */
    private final String name;

    /** The schema owning this profile, or null if global (owned by a model) */
    private final ImmutableSchema schema;

    private final List<String> inheritedNames = new ArrayList<>();

    /** The resolved inherited profiles, or null when not resolved. */
    private List<RankProfile> inherited;

    private MatchPhaseSettings matchPhase = null;
    private DiversitySettings diversity = null;

    protected Set<RankSetting> rankSettings = new java.util.LinkedHashSet<>();

    /** The ranking expression to be used for first phase */
    private RankingExpressionFunction firstPhaseRanking = null;

    /** The ranking expression to be used for second phase */
    private RankingExpressionFunction secondPhaseRanking = null;

    /** The ranking expression to be used for global-phase */
    private RankingExpressionFunction globalPhaseRanking = null;

    /** Number of hits to be reranked in second phase */
    private Optional<Integer> rerankCount = Optional.empty();

    /** Number of hits to be reranked in second phase across all nodes, */
    private Optional<Integer> totalRerankCount = Optional.empty();

    /** Number of hits to be reranked in global-phase, -1 means use default */
    private int globalPhaseRerankCount = -1;

    /** The number of hits per node for which to keep rank data in first phase, empty to use the default */
    private Optional<Integer> keepRankCount = Optional.empty();

    /** The number of hits across all nodes for which to keep rank data in first phase, empty to use keepRankCount */
    private Optional<Integer> totalKeepRankCount = Optional.empty();

    private int numThreadsPerSearch = -1;
    private int minHitsPerThread = -1;
    private int numSearchPartitions = -1;

    private Double termwiseLimit = null;
    private Double postFilterThreshold = null;
    private Double approximateThreshold = null;
    private Double filterFirstThreshold = null;
    private Double filterFirstExploration = null;
    private Double explorationSlack = null;
    private Boolean prefetchTensors = null;
    private Double targetHitsMaxAdjustmentFactor = null;
    private Double weakandStopwordLimit = null;
    private Boolean weakandAllowDropAll = null;
    private Double weakandAdjustTarget = null;
    private Double filterThreshold = null;

    /** The drop limit used to drop hits with rank score less than or equal to this value */
    private double rankScoreDropLimit = -Double.MAX_VALUE;
    private double secondPhaseRankScoreDropLimit = -Double.MAX_VALUE;
    private double globalPhaseRankScoreDropLimit = -Double.MAX_VALUE;

    private Set<ReferenceNode> summaryFeatures;
    private final List<String> inheritedSummaryFeaturesProfileNames = new ArrayList<>();

    private Set<ReferenceNode> matchFeatures;
    private Set<ReferenceNode> hiddenMatchFeatures;
    private final List<String> inheritedMatchFeaturesProfileNames = new ArrayList<>();

    private Set<ReferenceNode> rankFeatures;

    /** The properties of this - a multimap */
    private Map<String, List<RankProperty>> rankProperties = new LinkedHashMap<>();

    private Boolean ignoreDefaultRankFeatures = null;

    private Map<String, RankingExpressionFunction> functions = new LinkedHashMap<>();
    // This cache must be invalidated every time modifications are done to 'functions'.
    private CachedFunctions allFunctionsCached = null;

    private Map<Reference, Input> inputs = new LinkedHashMap<>();

    private Map<Reference, Constant> constants = new LinkedHashMap<>();

    private final Map<String, OnnxModel> onnxModels = new LinkedHashMap<>();

    private Set<String> filterFields = new HashSet<>();

    // Field-level `rank my_field { filter-threshold: ... }` that overrides the profile-level `filter-threshold` (if any)
    private Map<String, Double> explicitFieldRankFilterThresholds = new LinkedHashMap<>();

    private Map<String, ElementGap> explicitFieldRankElementGaps = new LinkedHashMap<>();

    private final RankProfileRegistry rankProfileRegistry;

    private final TypeSettings attributeTypes = new TypeSettings();

    private List<ImmutableSDField> allFieldsList;

    private Boolean strict;

    private Boolean useSignificanceModel;

    private final ApplicationPackage applicationPackage;
    private final DeployLogger deployLogger;

    /**
     * Creates a new rank profile for a particular schema
     *
     * @param name                the name of the new profile
     * @param schema              the schema owning this profile
     * @param rankProfileRegistry the {@link com.yahoo.schema.RankProfileRegistry} to use for storing
     *                            and looking up rank profiles.
     */
    public RankProfile(String name, Schema schema, RankProfileRegistry rankProfileRegistry) {
        this(name, Objects.requireNonNull(schema, "schema cannot be null"),
                schema.applicationPackage(), schema.getDeployLogger(),
                rankProfileRegistry);
    }

    /**
     * Creates a global rank profile
     *
     * @param name  the name of the new profile
     */
    public RankProfile(String name, Schema schema, ApplicationPackage applicationPackage, DeployLogger deployLogger,
                       RankProfileRegistry rankProfileRegistry) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.schema = schema;
        this.rankProfileRegistry = rankProfileRegistry;
        this.applicationPackage = applicationPackage;
        this.deployLogger = deployLogger;
    }

    public String name() { return name; }

    /** Returns the search definition owning this, or null if it is global */
    public ImmutableSchema schema() { return schema; }

    /** Returns the application this is part of */
    public ApplicationPackage applicationPackage() {
        return applicationPackage;
    }

    private Stream<ImmutableSDField> allFields() {
        if (schema == null) return Stream.empty();
        if (allFieldsList == null) {
            allFieldsList = schema.allFieldsList();
        }
        return allFieldsList.stream();
    }

    private Stream<ImmutableSDField> allImportedFields() {
        return schema != null ? schema.allImportedFields() : Stream.empty();
    }

    /**
     * Returns whether type checking should fail if this profile accesses query features that are
     * not defined in query profile types.
     *
     * Default is false.
     */
    public boolean isStrict() {
        Boolean declaredStrict = declaredStrict();
        if (declaredStrict != null) return declaredStrict;
        return false;
    }

    /** Returns the strict value declared in this or any parent profile. */
    public Boolean declaredStrict() {
        if (strict != null) return strict;
        return uniquelyInherited(p -> p.declaredStrict(), "strict").orElse(null);
    }

    public void setStrict(Boolean strict) {
        this.strict = strict;
    }

    public void setUseSignificanceModel(Boolean useSignificanceModel) {
        this.useSignificanceModel = useSignificanceModel;
    }

    public boolean useSignificanceModel() {
        if (useSignificanceModel != null) return useSignificanceModel;
        return uniquelyInherited(RankProfile::useSignificanceModel, "use-model")
                .orElse(false); // Disabled by default
    }

    /**
     * Adds a profile to those inherited by this.
     * The profile must belong to this schema (directly or by inheritance).
     */
    public void inherit(String inheritedName) {
        inherited = null;
        inheritedNames.add(inheritedName);
    }

    /** Returns the names of the profiles this inherits, if any. */
    public List<String> inheritedNames() { return Collections.unmodifiableList(inheritedNames); }

    /** Returns the rank profiles inherited by this. */
    private List<RankProfile> inherited() {
        if (inheritedNames.isEmpty()) return List.of();
        if (inherited != null) return inherited;

        inherited = resolveInheritedProfiles(schema);
        List<String> children = new ArrayList<>();
        children.add(createFullyQualifiedName());
        inherited.forEach(profile -> verifyNoInheritanceCycle(children, profile));
        return inherited;
    }

    private String createFullyQualifiedName() {
        return (schema != null)
                ? (schema.getName() + "." + name())
                : name();
    }

    private void verifyNoInheritanceCycle(List<String> children, RankProfile parent) {
        children.add(parent.createFullyQualifiedName());
        String root = children.get(0);
        if (root.equals(parent.createFullyQualifiedName()))
            throw new IllegalArgumentException("There is a cycle in the inheritance for rank-profile '" + root + "' = " + children);
        for (RankProfile parentInherited : parent.inherited())
            verifyNoInheritanceCycle(children, parentInherited);
    }

    private List<RankProfile> resolveInheritedProfiles(ImmutableSchema schema) {
        List<RankProfile> inherited = new ArrayList<>();
        for (String inheritedName : inheritedNames) {
            RankProfile inheritedProfile = schema ==  null
                                           ? rankProfileRegistry.getGlobal(inheritedName)
                                           : resolveInheritedProfile(schema, inheritedName);
            if (inheritedProfile == null)
                throw new IllegalArgumentException("rank-profile '" + name() + "' inherits '" + inheritedName +
                                                   "', but this is not found in " +
                                                   ((schema() != null) ? schema() : " global rank profiles"));
            inherited.add(inheritedProfile);
        }
        return inherited;
    }

    private RankProfile resolveInheritedProfile(ImmutableSchema schema, String inheritedName) {
        SDDocumentType documentType = schema.getDocument();
        if (documentType != null) {
            if (name.equals(inheritedName)) {
                // If you seemingly inherit yourself, you are actually referencing a rank-profile in one of your inherited schemas
                for (SDDocumentType baseType : documentType.getInheritedTypes()) {
                    RankProfile resolvedFromBase = rankProfileRegistry.resolve(baseType, inheritedName);
                    if (resolvedFromBase != null) return resolvedFromBase;
                }
            }
            return rankProfileRegistry.resolve(documentType, inheritedName);
        }
        return rankProfileRegistry.get(schema.getName(), inheritedName);
    }

    /** Returns whether this profile inherits (directly or indirectly) the given profile name. */
    public boolean inherits(String name) {
        for (RankProfile inheritedProfile : inherited()) {
            if (inheritedProfile.name().equals(name)) return true;
            if (inheritedProfile.inherits(name)) return true;
        }
        return false;
    }

    public void setMatchPhase(MatchPhaseSettings settings) {
        settings.checkValid();
        this.matchPhase = settings;
    }

    public MatchPhaseSettings getMatchPhase() {
        if (matchPhase != null) return matchPhase;
        return uniquelyInherited(RankProfile::getMatchPhase, "match phase settings").orElse(null);
    }
    public void setDiversity(DiversitySettings value) {
        value.checkValid();
        diversity = value;
    }
    public DiversitySettings getDiversity() {
        if (diversity != null) return diversity;
        return uniquelyInherited(RankProfile::getDiversity, "diversity settings").orElse(null);
    }

    /** Returns the uniquely determined property, where non-empty is defined as non-null */
    private <T> Optional<T> uniquelyInherited(Function<RankProfile, T> propertyRetriever,
                                              String propertyDescription) {
        return uniquelyInherited(propertyRetriever, Objects::nonNull, propertyDescription);
    }

    /**
     * Returns the property retrieved by the given function, if it is only present in a single unique variant
     * among all profiled inherited by this, or empty if not present.
     * Note that for properties that don't implement a values-based equals this reverts to the stricter condition that
     * only one inherited profile can define a non-empty value at all.
     *
     * @throws IllegalArgumentException if the inherited profiles defines multiple different values of the property
     */
    private <T> Optional<T> uniquelyInherited(Function<RankProfile, T> propertyRetriever,
                                              Predicate<T> nonEmptyValueFilter,
                                              String propertyDescription) {
        Set<T> uniqueProperties = inherited().stream()
                                             .map(propertyRetriever)
                                             .filter(nonEmptyValueFilter)
                                             .collect(Collectors.toSet());
        if (uniqueProperties.isEmpty()) return Optional.empty();
        if (uniqueProperties.size() == 1) return uniqueProperties.stream().findAny();
        throw new IllegalArgumentException("Only one of the profiles inherited by " + this + " can contain " +
                                           propertyDescription + ", but it is present in multiple");
    }

    public void addRankSetting(RankSetting rankSetting) {
        rankSettings.add(rankSetting);
    }

    public void addRankSetting(String fieldName, RankSetting.Type type, Object value) {
        addRankSetting(new RankSetting(fieldName, type, value));
    }

    /**
     * Returns the a rank setting of a field, or null if there is no such rank setting in this profile
     *
     * @param field the field whose settings to return
     * @param type  the type that the field is required to be
     * @return the rank setting found, or null
     */
    RankSetting getDeclaredRankSetting(String field, RankSetting.Type type) {
        for (Iterator<RankSetting> i = declaredRankSettingIterator(); i.hasNext(); ) {
            RankSetting setting = i.next();
            if (setting.getFieldName().equals(field) && setting.getType() == type) {
                return setting;
            }
        }
        return null;
    }

    /**
     * Returns a rank setting of field or index, or null if there is no such rank setting in this profile or one it
     * inherits
     *
     * @param field the field whose settings to return
     * @param type  the type that the field is required to be
     * @return the rank setting found, or null
     */
    public RankSetting getRankSetting(String field, RankSetting.Type type) {
        RankSetting rankSetting = getDeclaredRankSetting(field, type);
        if (rankSetting != null) return rankSetting;

        return uniquelyInherited(p -> p.getRankSetting(field, type), "rank setting " + type + " on " + field).orElse(null);
    }

    /**
     * Returns the rank settings in this rank profile
     *
     * @return an iterator for the declared rank setting
     */
    public Iterator<RankSetting> declaredRankSettingIterator() {
        return Collections.unmodifiableSet(rankSettings).iterator();
    }

    /**
     * Returns all settings in this profile or any profile it inherits
     *
     * @return an iterator for all rank settings of this
     */
    public Iterator<RankSetting> rankSettingIterator() {
        return rankSettings().iterator();
    }

    /**
     * Returns a snapshot of the rank settings of this and everything it inherits.
     * Changes to the returned set will not be reflected in this rank profile.
     */
    public Set<RankSetting> rankSettings() {
        Set<RankSetting> settings = new LinkedHashSet<>();
        for (RankProfile inheritedProfile : inherited()) {
            for (RankSetting setting : inheritedProfile.rankSettings()) {
                if (settings.contains(setting))
                    throw new IllegalArgumentException(setting + " is present in " + inheritedProfile + " inherited by " +
                                                       this + ", but is also present in another profile inherited by it");
                settings.add(setting);
            }
        }

        // TODO: Here we do things in the wrong order to not break tests. Reverse this.
        Set<RankSetting> finalSettings = new LinkedHashSet<>(rankSettings);
        finalSettings.addAll(settings);
        return finalSettings;
    }

    public void add(Constant constant) {
        constants.put(constant.name(), constant);
    }

    /** Returns an unmodifiable view of the constants declared in this */
    public Map<Reference, Constant> declaredConstants() { return Collections.unmodifiableMap(constants); }

    /** Returns an unmodifiable view of the constants available in this */
    public Map<Reference, Constant> constants() {
        Map<Reference, Constant> allConstants = new HashMap<>();
        for (var inheritedProfile : inherited()) {
            for (var constant : inheritedProfile.constants().values()) {
                if (allConstants.containsKey(constant.name()))
                    throw new IllegalArgumentException(constant + "' is present in " +
                                                       inheritedProfile + " inherited by " +
                                                       this + ", but is also present in another profile inherited by it");
                allConstants.put(constant.name(), constant);
            }
        }

        if (schema != null)
            allConstants.putAll(schema.constants());
        allConstants.putAll(constants);
        return allConstants;
    }

    public void add(OnnxModel model) {
        onnxModels.put(model.getName(), model);
    }

    /** Returns an unmodifiable map of the onnx models declared in this. */
    public Map<String, OnnxModel> declaredOnnxModels() { return onnxModels; }

    /** Returns an unmodifiable map of the onnx models available in this. */
    public Map<String, OnnxModel> onnxModels() {
        Map<String, OnnxModel> allModels = new HashMap<>();
        for (var inheritedProfile : inherited()) {
            for (var model : inheritedProfile.onnxModels().values()) {
                if (allModels.containsKey(model.getName()))
                    throw new IllegalArgumentException(model + "' is present in " +
                                                       inheritedProfile + " inherited by " +
                                                       this + ", but is also present in another profile inherited by it");
                allModels.put(model.getName(), model);
            }
        }

        if (schema != null)
            allModels.putAll(schema.onnxModels());
        allModels.putAll(onnxModels);
        return allModels;
    }

    public void addAttributeType(String attributeName, String attributeType) {
        attributeTypes.addType(attributeName, attributeType);
    }

    public Map<String, String> getAttributeTypes() {
        return attributeTypes.getTypes();
    }

    /**
     * Returns the ranking expression to use by this. This expression must not be edited.
     * Returns null if no expression is set.
     */
    public RankingExpression getFirstPhaseRanking() {
        RankingExpressionFunction function = getFirstPhase();
        if (function == null) return null;
        return function.function.getBody();
    }

    public RankingExpressionFunction getFirstPhase() {
        if (firstPhaseRanking != null) return firstPhaseRanking;
        return uniquelyInherited(RankProfile::getFirstPhase, "first-phase expression").orElse(null);
    }

    void setFirstPhaseRanking(RankingExpression rankingExpression) {
        this.firstPhaseRanking = new RankingExpressionFunction(new ExpressionFunction(FIRST_PHASE, List.of(), rankingExpression), false);
    }

    public void setFirstPhaseRanking(String expression) {
        try {
            firstPhaseRanking = new RankingExpressionFunction(parseRankingExpression(FIRST_PHASE, List.of(), expression), false);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid first-phase function", e);
        }
    }

    /**
     * Returns the ranking expression to use by this. This expression must not be edited.
     * Returns null if no expression is set.
     */
    public RankingExpression getSecondPhaseRanking() {
        RankingExpressionFunction function = getSecondPhase();
        if (function == null) return null;
        return function.function().getBody();
    }

    public RankingExpressionFunction getSecondPhase() {
        if (secondPhaseRanking != null) return secondPhaseRanking;
        return uniquelyInherited(RankProfile::getSecondPhase, "second-phase expression").orElse(null);
    }

    public void setSecondPhaseRanking(String expression) {
        try {
            secondPhaseRanking = new RankingExpressionFunction(parseRankingExpression(SECOND_PHASE, List.of(), expression), false);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid second-phase function", e);
        }
    }

    public RankingExpression getGlobalPhaseRanking() {
        RankingExpressionFunction function = getGlobalPhase();
        if (function == null) return null;
        return function.function().getBody();
    }

    public RankingExpressionFunction getGlobalPhase() {
        if (globalPhaseRanking != null) return globalPhaseRanking;
        return uniquelyInherited(RankProfile::getGlobalPhase, "global-phase expression").orElse(null);
    }

    public void setGlobalPhaseRanking(String expression) {
        try {
            globalPhaseRanking = new RankingExpressionFunction(parseRankingExpression(GLOBAL_PHASE, List.of(), expression), false);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid global-phase function", e);
        }
    }

    // TODO: Below we have duplicate methods for summary and match features: Encapsulate this in a single parametrized
    //       class instead (and probably make rank features work the same).

    /**
     * Sets the name this should inherit the summary features of.
     * Without setting this, this will either have the summary features of the single parent setting them,
     * or if summary features are set in this, only have the summary features in this.
     * With this set the resulting summary features of this will be the superset of those defined in this and
     * the final (with inheritance included) summary features of the given parent.
     * The profile must be one which is directly inherited by this.
     */
    public void addInheritedSummaryFeatures(String parentProfile) {
        if ( ! inheritedNames().contains(parentProfile))
            throw new IllegalArgumentException("This can only inherit the summary features of a directly inherited profile, " +
                                               "but is attempting to inherit '" + parentProfile);
        this.inheritedSummaryFeaturesProfileNames.add(parentProfile);
    }

    /**
     * Sets the name of a profile this should inherit the match features of.
     * Without setting this, this will either have the match features of the single parent setting them,
     * or if match features are set in this, only have the match features in this.
     * With this set the resulting match features of this will be the superset of those defined in this and
     * the final (with inheritance included) match features of the given parent.
     * The profile must be one which is directly inherited by this.
     */
    public void addInheritedMatchFeatures(String parentProfile) {
        if ( ! inheritedNames().contains(parentProfile))
            throw new IllegalArgumentException("This can only inherit the match features of a directly inherited profile," +
                                               "but is attempting to inherit '" + parentProfile);
        this.inheritedMatchFeaturesProfileNames.add(parentProfile);
    }

    /** Returns a read-only view of the summary features to use in this profile. This is never null */
    public Set<ReferenceNode> getSummaryFeatures() {
        if (inheritedSummaryFeaturesProfileNames.isEmpty()) {
            if (summaryFeatures != null)
                return Collections.unmodifiableSet(summaryFeatures);
            return uniquelyInherited(RankProfile::getSummaryFeatures, f -> ! f.isEmpty(), "summary features")
                    .orElse(Set.of());
        }
        Set<ReferenceNode> combined = new HashSet<>();
        for (String inheritName : inheritedSummaryFeaturesProfileNames) {
            RankProfile inherited = inherited().stream()
                    .filter(p -> p.name().equals(inheritName))
                    .findAny()
                    .orElseThrow();
            combined.addAll(inherited.getSummaryFeatures());
        }
        if (summaryFeatures != null)
            combined.addAll(summaryFeatures);
        return Collections.unmodifiableSet(combined);
    }

    /** Returns a read-only view of the match features to use in this profile. This is never null */
    public Set<ReferenceNode> getMatchFeatures() {
        if (inheritedMatchFeaturesProfileNames.isEmpty()) {
            if (matchFeatures != null)
                return Collections.unmodifiableSet(matchFeatures);
            return uniquelyInherited(RankProfile::getMatchFeatures, f -> ! f.isEmpty(), "match features")
                    .orElse(Set.of());
        }
        Set<ReferenceNode> combined = new HashSet<>();
        for (String inheritName : inheritedMatchFeaturesProfileNames) {
            RankProfile inherited = inherited().stream()
                    .filter(p -> p.name().equals(inheritName))
                    .findAny()
                    .orElseThrow();
            combined.addAll(inherited.getMatchFeatures());
        }
        if (matchFeatures != null)
            combined.addAll(matchFeatures);
        return Collections.unmodifiableSet(combined);
    }

    public Set<ReferenceNode> getHiddenMatchFeatures() {
        if (hiddenMatchFeatures != null) return Collections.unmodifiableSet(hiddenMatchFeatures);
        return uniquelyInherited(RankProfile::getHiddenMatchFeatures, f -> ! f.isEmpty(), "hidden match features")
                .orElse(Set.of());
    }

    private void addImplicitMatchFeatures(List<FeatureList> list) {
        if (hiddenMatchFeatures == null)
            hiddenMatchFeatures = new LinkedHashSet<>();
        var current = getMatchFeatures();
        for (var features : list) {
            for (ReferenceNode feature : features) {
                if (! current.contains(feature)) {
                    hiddenMatchFeatures.add(feature);
                }
            }
        }
    }

    /** Adds the content of the given feature list to the internal list of summary features. */
    public void addSummaryFeatures(FeatureList features) {
        if (summaryFeatures == null)
            summaryFeatures = new LinkedHashSet<>();
        for (ReferenceNode feature : features) {
            summaryFeatures.add(feature);
        }
    }

    /** Adds the content of the given feature list to the internal list of match features. */
    public void addMatchFeatures(FeatureList features) {
        if (matchFeatures == null)
            matchFeatures = new LinkedHashSet<>();
        for (ReferenceNode feature : features) {
            matchFeatures.add(feature);
        }
    }

    /** Returns a read-only view of the rank features to use in this profile. This is never null */
    public Set<ReferenceNode> getRankFeatures() {
        if (rankFeatures != null) return Collections.unmodifiableSet(rankFeatures);
        return uniquelyInherited(RankProfile::getRankFeatures, f -> ! f.isEmpty(), "summary-features")
                .orElse(Set.of());
    }

    /**
     * Adds the content of the given feature list to the internal list of rank features.
     *
     * @param features The features to add.
     */
    public void addRankFeatures(FeatureList features) {
        if (rankFeatures == null)
            rankFeatures = new LinkedHashSet<>();
        for (ReferenceNode feature : features) {
            rankFeatures.add(feature);
        }
    }

    /** Returns a read only flattened list view of the rank properties to use in this profile. This is never null. */
    public List<RankProperty> getRankProperties() {
        List<RankProperty> properties = new ArrayList<>();
        for (List<RankProperty> propertyList : getRankPropertyMap().values()) {
            properties.addAll(propertyList);
        }
        return Collections.unmodifiableList(properties);
    }

    /** Returns a read only map view of the rank properties to use in this profile. This is never null. */
    public Map<String, List<RankProperty>> getRankPropertyMap() {
        if (rankProperties.isEmpty() && inherited().isEmpty()) return Map.of();
        if (inherited().isEmpty()) return Collections.unmodifiableMap(rankProperties);

        var inheritedProperties = uniquelyInherited(RankProfile::getRankPropertyMap, m -> ! m.isEmpty(), "rank-properties")
                .orElse(Map.of());
        if (rankProperties.isEmpty()) return inheritedProperties;

        // Neither is null
        Map<String, List<RankProperty>> combined = new LinkedHashMap<>(inheritedProperties);
        combined.putAll(rankProperties); // Don't combine values across inherited properties
        return Collections.unmodifiableMap(combined);
    }

    public void addRankProperty(String name, String parameter) {
        addRankProperty(new RankProperty(name, parameter));
    }

    /*
     * set a rank-property that should be a single-value parameter;
     * if the same name is used multiple times, that parameter must be identical each time.
     */
    public void setRankProperty(String name, String parameter) {
        var old = rankProperties.get(name);
        if (old != null) {
            if (old.size() != 1) {
                throw new IllegalStateException("setRankProperty used for multi-valued property " + name);
            }
            var oldVal = old.get(0).getValue();
            if (! oldVal.equals(parameter)) {
                throw new IllegalArgumentException("setRankProperty would change property " + name + " from " + oldVal + " to " + parameter);
            }
        } else {
            addRankProperty(new RankProperty(name, parameter));
        }
    }

    private void addRankProperty(RankProperty rankProperty) {
        // Just the usual multimap semantics here
        rankProperties.computeIfAbsent(rankProperty.getName(), (String key) -> new ArrayList<>(1)).add(rankProperty);
    }

    public void setRerankCount(int rerankCount) { this.rerankCount = Optional.of(rerankCount); }

    public void setTotalRerankCount(int totalRerankCount) { this.totalRerankCount = Optional.of(totalRerankCount); }

    public Optional<Integer> getRerankCount() {
        if (rerankCount.isPresent()) return rerankCount;
        return uniquelyInherited(RankProfile::getRerankCount, Optional::isPresent, "rerank-count").orElse(Optional.empty());
    }

    public Optional<Integer> getTotalRerankCount() {
        if (totalRerankCount.isPresent()) return totalRerankCount;
        return uniquelyInherited(RankProfile::getTotalRerankCount, Optional::isPresent, "total-rerank-count").orElse(Optional.empty());
    }

    public void setGlobalPhaseRerankCount(int count) { this.globalPhaseRerankCount = count; }

    public int getGlobalPhaseRerankCount() {
        if (globalPhaseRerankCount >= 0) return globalPhaseRerankCount;
        return uniquelyInherited(RankProfile::getGlobalPhaseRerankCount, c -> c >= 0, "global-phase rerank-count").orElse(-1);
    }

    public void setNumThreadsPerSearch(int numThreads) { this.numThreadsPerSearch = numThreads; }

    public int getNumThreadsPerSearch() {
        if (numThreadsPerSearch >= 0) return numThreadsPerSearch;
        return uniquelyInherited(RankProfile::getNumThreadsPerSearch, n -> n >= 0, "num-threads-per-search")
                .orElse(-1);
    }

    public void setMinHitsPerThread(int minHits) { this.minHitsPerThread = minHits; }

    public int getMinHitsPerThread() {
        if (minHitsPerThread >= 0) return minHitsPerThread;
        return uniquelyInherited(RankProfile::getMinHitsPerThread, n -> n >= 0, "min-hits-per-search").orElse(-1);
    }

    public void setNumSearchPartitions(int numSearchPartitions) { this.numSearchPartitions = numSearchPartitions; }

    public int getNumSearchPartitions() {
        if (numSearchPartitions >= 0) return numSearchPartitions;
        return uniquelyInherited(RankProfile::getNumSearchPartitions, n -> n >= 0, "num-search-partitions").orElse(-1);
    }

    public void setTermwiseLimit(double termwiseLimit) { this.termwiseLimit = termwiseLimit; }
    public void setPostFilterThreshold(double threshold) { this.postFilterThreshold = threshold; }
    public void setApproximateThreshold(double threshold) { this.approximateThreshold = threshold; }
    public void setFilterFirstThreshold(double threshold) { this.filterFirstThreshold = threshold; }
    public void setFilterFirstExploration(double exploration) { this.filterFirstExploration = exploration; }
    public void setExplorationSlack(double slack) { this.explorationSlack = slack; }
    public void setPrefetchTensors(boolean value) { this.prefetchTensors = value; }
    public void setTargetHitsMaxAdjustmentFactor(double factor) { this.targetHitsMaxAdjustmentFactor = factor; }
    public void setWeakandStopwordLimit(double limit) { this.weakandStopwordLimit = limit; }
    public void setWeakandAdjustTarget(double target) { this.weakandAdjustTarget = target; }
    public void setWeakandAllowDropAll(boolean value) { this.weakandAllowDropAll = value; }
    public void setFilterThreshold(double threshold) { this.filterThreshold = threshold; }

    public OptionalDouble getTermwiseLimit() {
        if (termwiseLimit != null) return OptionalDouble.of(termwiseLimit);
        return uniquelyInherited(RankProfile::getTermwiseLimit, OptionalDouble::isPresent, "termwise-limit")
                .orElse(OptionalDouble.empty());
    }

    public OptionalDouble getPostFilterThreshold() {
        if (postFilterThreshold != null) {
            return OptionalDouble.of(postFilterThreshold);
        }
        return uniquelyInherited(RankProfile::getPostFilterThreshold, OptionalDouble::isPresent, "post-filter-threshold").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getApproximateThreshold() {
        if (approximateThreshold != null) {
            return OptionalDouble.of(approximateThreshold);
        }
        return uniquelyInherited(RankProfile::getApproximateThreshold, OptionalDouble::isPresent, "approximate-threshold").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getFilterFirstThreshold() {
        if (filterFirstThreshold != null) {
            return OptionalDouble.of(filterFirstThreshold);
        }
        return uniquelyInherited(RankProfile::getFilterFirstThreshold, OptionalDouble::isPresent, "filter-first-threshold").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getFilterFirstExploration() {
        if (filterFirstExploration != null) {
            return OptionalDouble.of(filterFirstExploration);
        }
        return uniquelyInherited(RankProfile::getFilterFirstExploration, OptionalDouble::isPresent, "filter-first-exploration").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getExplorationSlack() {
        if (explorationSlack != null) {
            return OptionalDouble.of(explorationSlack);
        }
        return uniquelyInherited(RankProfile::getExplorationSlack, OptionalDouble::isPresent, "exploration-slack").orElse(OptionalDouble.empty());
    }

    public Boolean getPrefetchTensors() {
        if (prefetchTensors != null) {
            return prefetchTensors;
        }
        return uniquelyInherited(RankProfile::getPrefetchTensors, "prefetch-tensors").orElse(null);
    }

    public OptionalDouble getTargetHitsMaxAdjustmentFactor() {
        if (targetHitsMaxAdjustmentFactor != null) {
            return OptionalDouble.of(targetHitsMaxAdjustmentFactor);
        }
        return uniquelyInherited(RankProfile::getTargetHitsMaxAdjustmentFactor, OptionalDouble::isPresent, "target-hits-max-adjustment-factor").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getWeakandStopwordLimit() {
        if (weakandStopwordLimit != null) {
            return OptionalDouble.of(weakandStopwordLimit);
        }
        return uniquelyInherited(RankProfile::getWeakandStopwordLimit, OptionalDouble::isPresent, "weakand-stopword-limit").orElse(OptionalDouble.empty());
    }

    public Boolean getWeakandAllowDropAll() {
        if (weakandAllowDropAll != null) {
            return weakandAllowDropAll;
        }
        return uniquelyInherited(RankProfile::getWeakandAllowDropAll, "weakand-allow-drop-all").orElse(null);
    }

    public OptionalDouble getWeakandAdjustTarget() {
        if (weakandAdjustTarget != null) {
            return OptionalDouble.of(weakandAdjustTarget);
        }
        return uniquelyInherited(RankProfile::getWeakandAdjustTarget, OptionalDouble::isPresent, "weakand-adjust-target").orElse(OptionalDouble.empty());
    }

    public OptionalDouble getFilterThreshold() {
        if (filterThreshold != null) {
            return OptionalDouble.of(filterThreshold);
        }
        return uniquelyInherited(RankProfile::getFilterThreshold, OptionalDouble::isPresent, "filter-threshold").orElse(OptionalDouble.empty());
    }

    /** Whether we should ignore the default rank features. Set to null to use inherited */
    public void setIgnoreDefaultRankFeatures(Boolean ignoreDefaultRankFeatures) {
        this.ignoreDefaultRankFeatures = ignoreDefaultRankFeatures;
    }

    public Boolean getIgnoreDefaultRankFeatures() {
        if (ignoreDefaultRankFeatures != null) return ignoreDefaultRankFeatures;
        return uniquelyInherited(RankProfile::getIgnoreDefaultRankFeatures, "ignore-default-rank-features").orElse(false);
    }

    public void setKeepRankCount(int count) { this.keepRankCount = Optional.of(count); }

    public Optional<Integer> getKeepRankCount() {
        if (keepRankCount.isPresent()) return keepRankCount;
        return uniquelyInherited(RankProfile::getKeepRankCount, Optional::isPresent, "keep-rank-count").orElse(Optional.empty());
    }

    public void setTotalKeepRankCount(int totalKeepRankCount) { this.totalKeepRankCount = Optional.of(totalKeepRankCount); }

    public Optional<Integer> getTotalKeepRankCount() {
        if (totalKeepRankCount.isPresent()) return totalKeepRankCount;
        return uniquelyInherited(RankProfile::getTotalKeepRankCount, Optional::isPresent, "total-keep-rank-count").orElse(Optional.empty());
    }

    public void setRankScoreDropLimit(double rankScoreDropLimit) { this.rankScoreDropLimit = rankScoreDropLimit; }

    public double getRankScoreDropLimit() {
        if (rankScoreDropLimit > -Double.MAX_VALUE) return rankScoreDropLimit;
        return uniquelyInherited(RankProfile::getRankScoreDropLimit, c -> c > -Double.MAX_VALUE, "rank.score-drop-limit")
                .orElse(rankScoreDropLimit);
    }

    public void setSecondPhaseRankScoreDropLimit(double limit) { this.secondPhaseRankScoreDropLimit = limit; }

    public double getSecondPhaseRankScoreDropLimit() {
        if (secondPhaseRankScoreDropLimit > -Double.MAX_VALUE) {
            return secondPhaseRankScoreDropLimit;
        }
        return uniquelyInherited(RankProfile::getSecondPhaseRankScoreDropLimit, c -> c > -Double.MAX_VALUE, "second-phase rank-score-drop-limit")
                .orElse(secondPhaseRankScoreDropLimit);
    }

    public void setGlobalPhaseRankScoreDropLimit(double limit) { this.globalPhaseRankScoreDropLimit = limit; }

    public double getGlobalPhaseRankScoreDropLimit() {
        if (globalPhaseRankScoreDropLimit > -Double.MAX_VALUE) {
            return globalPhaseRankScoreDropLimit;
        }
        return uniquelyInherited(RankProfile::getGlobalPhaseRankScoreDropLimit, c -> c > -Double.MAX_VALUE, "global-phase rank-score-drop-limit")
                .orElse(globalPhaseRankScoreDropLimit);
    }

    public void addFunction(String name, List<String> arguments, String expression, boolean inline) {
        try {
            addFunction(parseRankingExpression(name, arguments, expression), inline);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Invalid function '" + name + "'", e);
        }
    }

    /** Adds a function and returns it */
    public RankingExpressionFunction addFunction(ExpressionFunction function, boolean inline) {
        RankingExpressionFunction rankingExpressionFunction = new RankingExpressionFunction(function, inline);
        if (functions.containsKey(function.getName())) {
            deployLogger.log(Level.WARNING, "Function '" + function.getName() + "' is defined twice " +
                                            "in rank profile '" + this.name + "'");
        }
        functions.put(function.getName(), rankingExpressionFunction);
        allFunctionsCached = null;
        return rankingExpressionFunction;
    }

    /**
     * Adds the type of an input feature consumed by this profile.
     * All inputs must either be declared through this or in query profile types,
     * otherwise they are assumes to be scalars.
     */
    public void addInput(Reference reference, Input input) {
        if (inputs.containsKey(reference)) {
            Input existing = inputs().get(reference);
            if (! input.equals(existing))
                throw new IllegalArgumentException("Duplicate input: Has both " + input + " and existing " + existing);
        }
        inputs.put(reference, input);
    }

    /** Returns the inputs of this, which also includes all inputs of the parents of this. */
    // This is less restrictive than most other constructs in allowing inputs to be defined in all parent profiles
    // because inputs are tied closer to functions than the profile itself.
    public Map<Reference, Input> inputs() {
        if (inputs.isEmpty() && inherited().isEmpty()) return Map.of();
        if (inherited().isEmpty()) return Collections.unmodifiableMap(inputs);

        // Combine
        Map<Reference, Input> allInputs = new LinkedHashMap<>();

        for (var inheritedProfile : inherited()) {
            for (var input : inheritedProfile.inputs().entrySet()) {
                Input existing = allInputs.get(input.getKey());
                if (existing != null && ! existing.equals(input.getValue()))
                    throw new IllegalArgumentException(this + " inherits " + inheritedProfile + " which contains " +
                                                       input.getValue() + ", but this is already defined as " +
                                                       existing + " in another profile this inherits");
                allInputs.put(input.getKey(), input.getValue());
            }
        }
        allInputs.putAll(inputs);
        return Collections.unmodifiableMap(allInputs);
    }

    public static class MutateOperation {
        public enum Phase { on_match, on_first_phase, on_second_phase, on_summary}
        final Phase phase;
        final String attribute;
        final String operation;
        public MutateOperation(Phase phase, String attribute, String operation) {
            this.phase = phase;
            this.attribute = attribute;
            this.operation = operation;
        }
    }
    private final List<MutateOperation> mutateOperations = new ArrayList<>();

    public void addMutateOperation(MutateOperation op) {
        mutateOperations.add(op);
        String prefix = "vespa.mutate." + op.phase.toString();
        addRankProperty(prefix + ".attribute", op.attribute);
        addRankProperty(prefix + ".operation", op.operation);
    }

    public List<MutateOperation> getMutateOperations() { return mutateOperations; }

    public RankingExpressionFunction findFunction(String name) {
        RankingExpressionFunction function = functions.get(name);
        if (function != null) return function;
        return uniquelyInherited(p -> p.findFunction(name), "function '" + name + "'").orElse(null);
    }

    /** Returns an unmodifiable snapshot of the functions in this */
    public Map<String, RankingExpressionFunction> getFunctions() {
        updateCachedFunctions();
        return allFunctionsCached.allRankingExpressionFunctions;
    }
    private ImmutableMap<String, ExpressionFunction> getExpressionFunctions() {
        updateCachedFunctions();
        return allFunctionsCached.allExpressionFunctions;
    }
    private void updateCachedFunctions() {
        if (needToUpdateFunctionCache()) {
            allFunctionsCached = new CachedFunctions(gatherAllFunctions());
        }
    }

    private Map<String, RankingExpressionFunction> gatherAllFunctions() {
        if (functions.isEmpty() && inherited().isEmpty()) return Map.of();
        if (inherited().isEmpty()) return Collections.unmodifiableMap(new LinkedHashMap<>(functions));

        // Combine
        Map<String, RankingExpressionFunction> allFunctions = new LinkedHashMap<>();
        for (var inheritedProfile : inherited()) {
            for (var function : inheritedProfile.getFunctions().entrySet()) {
                if (allFunctions.containsKey(function.getKey()))
                    throw new IllegalArgumentException(this + " inherits " + inheritedProfile + " which contains " +
                                                       function.getValue() + ", but this function is already " +
                                                       "defined in another profile this inherits");
                allFunctions.put(function.getKey(), function.getValue());
            }
        }
        allFunctions.putAll(functions);
        return Collections.unmodifiableMap(allFunctions);
    }

    private boolean needToUpdateFunctionCache() {
        if (inherited().stream().anyMatch(RankProfile::needToUpdateFunctionCache)) return true;
        return allFunctionsCached == null;
    }

    public Set<String> filterFields() { return filterFields; }

    /** Returns all filter fields in this profile and any profile it inherits. */
    public Set<String> allFilterFields() {
        Set<String> inheritedFilterFields = uniquelyInherited(RankProfile::allFilterFields, fields -> ! fields.isEmpty(),
                                                              "filter fields").orElse(Set.of());

        if (inheritedFilterFields.isEmpty()) return Collections.unmodifiableSet(filterFields);

        Set<String> combined = new LinkedHashSet<>(inheritedFilterFields);
        combined.addAll(filterFields());
        return combined;
    }

    public void setExplicitFieldRankFilterThresholds(Map<String, Double> fieldFilterThresholds) {
        explicitFieldRankFilterThresholds = new LinkedHashMap<>(fieldFilterThresholds);
    }

    public Map<String, Double> explicitFieldRankFilterThresholds() {
        return explicitFieldRankFilterThresholds;
    }

    public void setExplicitFieldRankElementGaps(Map<String, ElementGap> fieldElementGaps) {
        explicitFieldRankElementGaps = new LinkedHashMap<>(fieldElementGaps);
    }

    public Map<String, ElementGap> explicitFieldRankElementGaps() {
        if (explicitFieldRankElementGaps.isEmpty() && inherited().isEmpty()) return Map.of();
        if (inherited().isEmpty()) return Collections.unmodifiableMap(explicitFieldRankElementGaps);

        var inheritedElementGaps = uniquelyInherited(RankProfile::explicitFieldRankElementGaps, m -> ! m.isEmpty(), "element-gap")
                                           .orElse(Map.of());
        if (explicitFieldRankElementGaps.isEmpty()) return inheritedElementGaps;

        // Neither is empty
        Map<String, ElementGap> combined = new LinkedHashMap<>(inheritedElementGaps);
        combined.putAll(explicitFieldRankElementGaps);
        return Collections.unmodifiableMap(combined);
    }

    public Map<String, ElementGap> getFieldRankElementGaps() {
        Map<String, ElementGap> unionOfInheritedGaps = new LinkedHashMap<>();
        for (var parent : inherited()) {
            var fromParent = parent.getFieldRankElementGaps();
            for (var entry : fromParent.entrySet()) {
                String fieldName = entry.getKey();
                ElementGap gap = entry.getValue();
                ElementGap old = unionOfInheritedGaps.get(fieldName);
                if (old == null) {
                    unionOfInheritedGaps.put(fieldName, gap);
                } else if (! old.equals(gap)) {
                    // will we override it?
                    if (explicitFieldRankElementGaps == null || ! explicitFieldRankElementGaps.containsKey(fieldName)) {
                        throw new IllegalArgumentException("Several of the profiles inherited by " + this +
                                                           " contains element-gap for field " + fieldName + ", cannot resolve conflict");
                    }
                }
            }
        }
        if (explicitFieldRankElementGaps != null) {
            unionOfInheritedGaps.putAll(explicitFieldRankElementGaps);
        }
        return unionOfInheritedGaps;
    }

    private ExpressionFunction parseRankingExpression(String name, List<String> arguments, String expression) throws ParseException {
        if (expression.trim().isEmpty())
            throw new ParseException("Empty expression");

        try (Reader rankingExpressionReader = openRankingExpressionReader(name, expression.trim())) {
            return new ExpressionFunction(name, arguments, new RankingExpression(name, rankingExpressionReader));
        }
        catch (com.yahoo.searchlib.rankingexpression.parser.ParseException e) {
            ParseException exception = new ParseException("Invalid expression '" + expression.trim());
            throw (ParseException)exception.initCause(e);
        }
        catch (IOException e) {
            throw new RuntimeException("IOException parsing ranking expression '" + name + "'", e);
        }
    }

    private static String extractFileName(String expression) {
        String fileName = expression.substring("file:".length()).trim();
        if ( ! fileName.endsWith(ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX))
            fileName = fileName + ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX;

        return fileName;
    }

    private Reader openRankingExpressionReader(String expName, String expression) {
        if (!expression.startsWith("file:")) return new StringReader(expression);

        String fileName = extractFileName(expression);
        Path.fromString(fileName); // No ".."
        if (fileName.contains("/")) // See ticket 4102122
            throw new IllegalArgumentException("In " + name() + ", " + expName + ", ranking references file '" +
                                               fileName + "' in a different directory, which is not supported.");

        return schema.getRankingExpression(fileName);
    }

    /** Shallow clones this */
    @Override
    public RankProfile clone() {
        try {
            RankProfile clone = (RankProfile)super.clone();
            clone.rankSettings = new LinkedHashSet<>(this.rankSettings);
            clone.matchPhase = this.matchPhase; // hmm?
            clone.diversity = this.diversity;
            clone.summaryFeatures = summaryFeatures != null ? new LinkedHashSet<>(this.summaryFeatures) : null;
            clone.matchFeatures = matchFeatures != null ? new LinkedHashSet<>(this.matchFeatures) : null;
            clone.rankFeatures = rankFeatures != null ? new LinkedHashSet<>(this.rankFeatures) : null;
            clone.rankProperties = new LinkedHashMap<>(this.rankProperties);
            clone.inputs = new LinkedHashMap<>(this.inputs);
            clone.functions = new LinkedHashMap<>(this.functions);
            clone.allFunctionsCached = null;
            clone.filterFields = new HashSet<>(this.filterFields);
            clone.constants = new HashMap<>(this.constants);
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    /**
     * Returns a copy of this where the content is optimized for execution.
     * Compiled profiles should never be modified.
     */
    public RankProfile compile(QueryProfileRegistry queryProfiles, ImportedMlModels importedModels) {
        try {
            RankProfile compiled = this.clone();
            compiled.compileThis(queryProfiles, importedModels);
            return compiled;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rank profile '" + name() + "' is invalid", e);
        }
    }

    private void compileThis(QueryProfileRegistry queryProfiles, ImportedMlModels importedModels) {
        checkNameCollisions(getFunctions(), constants());
        ExpressionTransforms expressionTransforms = new ExpressionTransforms();

        Map<Reference, TensorType> featureTypes = featureTypes();
        // Function compiling first pass: compile inline functions without resolving other functions
        Map<String, RankingExpressionFunction> inlineFunctions =
                compileFunctions(this::getInlineFunctions, queryProfiles, featureTypes, importedModels, Map.of(), expressionTransforms);

        firstPhaseRanking = compile(this.getFirstPhase(), queryProfiles, featureTypes, importedModels, constants(), inlineFunctions, expressionTransforms);
        secondPhaseRanking = compile(this.getSecondPhase(), queryProfiles, featureTypes, importedModels, constants(), inlineFunctions, expressionTransforms);
        globalPhaseRanking = compile(this.getGlobalPhase(), queryProfiles, featureTypes, importedModels, constants(), inlineFunctions, expressionTransforms);

        // Function compiling second pass: compile all functions and insert previously compiled inline functions
        // TODO: This merges all functions from inherited profiles too and erases inheritance information. Not good.
        functions = compileFunctions(this::getFunctions, queryProfiles, featureTypes, importedModels, inlineFunctions, expressionTransforms);
        allFunctionsCached = null;

        var context = new RankProfileTransformContext(this,
                                                      queryProfiles,
                                                      featureTypes,
                                                      importedModels,
                                                      constants(),
                                                      inlineFunctions);
        var allNormalizers = getFeatureNormalizers();
        verifyNoNormalizers("first-phase expression", firstPhaseRanking, allNormalizers, context);
        verifyNoNormalizers("second-phase expression", secondPhaseRanking, allNormalizers, context);
        for (ReferenceNode mf : getMatchFeatures()) {
            verifyNoNormalizers("match-feature " + mf, mf, allNormalizers, context);
        }
        for (ReferenceNode sf : getSummaryFeatures()) {
            verifyNoNormalizers("summary-feature " + sf, sf, allNormalizers, context);
        }
        if (globalPhaseRanking != null) {
            var needInputs = new HashSet<String>();
            Set<String> userDeclaredMatchFeatures = new HashSet<>();
            for (ReferenceNode mf : getMatchFeatures()) {
                userDeclaredMatchFeatures.add(mf.toString());
            }
            var recorder = new InputRecorder(needInputs);
            recorder.alreadyMatchFeatures(userDeclaredMatchFeatures);
            recorder.addKnownNormalizers(allNormalizers.keySet());
            recorder.process(globalPhaseRanking.function().getBody(), context);
            for (var normalizerName : recorder.normalizersUsed()) {
                var normalizer = allNormalizers.get(normalizerName);
                var func = functions.get(normalizer.input());
                if (func != null) {
                    verifyNoNormalizers("normalizer input " + normalizer.input(), func, allNormalizers, context);
                    if (! userDeclaredMatchFeatures.contains(normalizer.input())) {
                        var subRecorder = new InputRecorder(needInputs);
                        subRecorder.alreadyMatchFeatures(userDeclaredMatchFeatures);
                        subRecorder.process(func.function().getBody(), context);
                    }
                } else {
                    needInputs.add(normalizer.input());
                }
            }
            List<FeatureList> addIfMissing = new ArrayList<>();
            for (String input : needInputs) {
                if (input.startsWith("constant(") || input.startsWith("query(") || input.equals("relevanceScore")) {
                    continue;
                }
                try {
                    addIfMissing.add(new FeatureList(input));
                } catch (com.yahoo.searchlib.rankingexpression.parser.ParseException e) {
                    throw new IllegalArgumentException("invalid input in global-phase expression: "+input);
                }
            }
            addImplicitMatchFeatures(addIfMissing);
        }
    }

    private void checkNameCollisions(Map<String, RankingExpressionFunction> functions, Map<Reference, Constant> constants) {
        for (var functionEntry : functions.entrySet()) {
            if (constants.containsKey(FeatureNames.asConstantFeature(functionEntry.getKey())))
                throw new IllegalArgumentException("Cannot have both a constant and function named '" +
                                                   functionEntry.getKey() + "'");
        }
    }

    private Map<String, RankingExpressionFunction> getInlineFunctions() {
        return getFunctions().entrySet().stream().filter(x -> x.getValue().inline())
                             .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, RankingExpressionFunction> compileFunctions(Supplier<Map<String, RankingExpressionFunction>> functions,
                                                                    QueryProfileRegistry queryProfiles,
                                                                    Map<Reference, TensorType> featureTypes,
                                                                    ImportedMlModels importedModels,
                                                                    Map<String, RankingExpressionFunction> inlineFunctions,
                                                                    ExpressionTransforms expressionTransforms) {
        Map<String, RankingExpressionFunction> compiledFunctions = new LinkedHashMap<>();
        Map.Entry<String, RankingExpressionFunction> entry;
        // Compile all functions. Why iterate in such a complicated way?
        // Because some functions (imported models adding generated functions) may add other functions during compiling.
        // A straightforward iteration will either miss those functions, or may cause a ConcurrentModificationException
        while (null != (entry = findUncompiledFunction(functions.get(), compiledFunctions.keySet()))) {
            RankingExpressionFunction rankingExpressionFunction = entry.getValue();
            RankingExpressionFunction compiled = compile(rankingExpressionFunction, queryProfiles, featureTypes,
                                                         importedModels, constants(), inlineFunctions,
                                                         expressionTransforms);
            compiledFunctions.put(entry.getKey(), compiled);
        }
        return compiledFunctions;
    }

    private static Map.Entry<String, RankingExpressionFunction> findUncompiledFunction(Map<String, RankingExpressionFunction> functions,
                                                                                       Set<String> compiledFunctionNames) {
        for (Map.Entry<String, RankingExpressionFunction> entry : functions.entrySet()) {
            if ( ! compiledFunctionNames.contains(entry.getKey()))
                return entry;
        }
        return null;
    }

    private RankingExpressionFunction compile(RankingExpressionFunction function,
                                              QueryProfileRegistry queryProfiles,
                                              Map<Reference, TensorType> featureTypes,
                                              ImportedMlModels importedModels,
                                              Map<Reference, Constant> constants,
                                              Map<String, RankingExpressionFunction> inlineFunctions,
                                              ExpressionTransforms expressionTransforms) {
        if (function == null) return null;
        RankProfileTransformContext context = new RankProfileTransformContext(this,
                                                                              queryProfiles,
                                                                              featureTypes,
                                                                              importedModels,
                                                                              constants,
                                                                              inlineFunctions);
        RankingExpression expression = expressionTransforms.transform(function.function().getBody(), context);
        for (Map.Entry<String, String> rankProperty : context.rankProperties().entrySet()) {
            setRankProperty(rankProperty.getKey(), rankProperty.getValue());
        }
        return function.withExpression(expression);
    }

    /**
     * Creates a context containing the type information of all constants, attributes and query profiles
     * referable from this rank profile.
     */
    public MapEvaluationTypeContext typeContext(QueryProfileRegistry queryProfiles) {
        return typeContext(queryProfiles, featureTypes());
    }

    public MapEvaluationTypeContext typeContext() { return typeContext(new QueryProfileRegistry()); }

    private Map<Reference, TensorType> featureTypes() {
        Map<Reference, TensorType> featureTypes = inputs().values().stream()
                .collect(Collectors.toMap(Input::name,
                                          input -> input.type().tensorType()));
        allFields().forEach(field -> addAttributeFeatureTypes(field, featureTypes));
        allImportedFields().forEach(field -> addAttributeFeatureTypes(field, featureTypes));
        return featureTypes;
    }

    public MapEvaluationTypeContext typeContext(QueryProfileRegistry queryProfiles,
                                                Map<Reference, TensorType> featureTypes) {
        MapEvaluationTypeContext context = new MapEvaluationTypeContext(getExpressionFunctions(), featureTypes);

        constants().forEach((k, v) -> context.setType(k, v.type()));

        // Add query features from all rank profile types
        for (QueryProfileType queryProfileType : queryProfiles.getTypeRegistry().allComponents()) {
            for (FieldDescription field : queryProfileType.declaredFields().values()) {
                TensorType type = field.getType().asTensorType();
                Optional<Reference> feature = Reference.simple(field.getName());
                if (feature.isEmpty() || ! feature.get().name().equals("query")) continue;
                if (featureTypes.containsKey(feature.get())) continue; // Explicit feature types (from inputs) overrides

                TensorType existingType = context.getType(feature.get());
                if ( ! Objects.equals(existingType, context.defaultTypeOf(feature.get())))
                    type = existingType.dimensionwiseGeneralizationWith(type).orElseThrow( () ->
                        new IllegalArgumentException(queryProfileType + " contains query feature " + feature.get() +
                                                     " with type " + field.getType().asTensorType() +
                                                     ", but this is already defined in another query profile with type " +
                                                     context.getType(feature.get())));
                context.setType(feature.get(), type);
            }
        }

        // Add output types for ONNX models
        for (var model : onnxModels().values()) {
            Arguments args = new Arguments(new ReferenceNode(model.getName()));
            Map<String, TensorType> inputTypes = resolveOnnxInputTypes(model, context);

            TensorType defaultOutputType = model.getTensorType(model.getDefaultOutput(), inputTypes);
            context.setType(new Reference("onnx", args, null), defaultOutputType);

            for (Map.Entry<String, String> mapping : model.getOutputMap().entrySet()) {
                TensorType type = model.getTensorType(mapping.getKey(), inputTypes);
                context.setType(new Reference("onnx", args, mapping.getValue()), type);
            }
        }
        return context;
    }

    private Map<String, TensorType> resolveOnnxInputTypes(OnnxModel model, MapEvaluationTypeContext context) {
        Map<String, TensorType> inputTypes = new HashMap<>();
        for (String onnxInputName : model.getInputMap().keySet()) {
            resolveOnnxInputType(onnxInputName, model, context).ifPresent(type -> inputTypes.put(onnxInputName, type));
        }
        return inputTypes;
    }

    private Optional<TensorType> resolveOnnxInputType(String onnxInputName, OnnxModel model, MapEvaluationTypeContext context) {
        String source = model.getInputMap().get(onnxInputName);
        if (source != null) {
            // Source is either a simple reference (query/attribute/constant/rankingExpression)...
            Optional<Reference> reference = Reference.simple(source);
            if (reference.isPresent()) {
                if (reference.get().isSimpleRankingExpressionWrapper()) {
                    source = reference.get().simpleArgument().get();  // look up function below
                } else {
                    return Optional.of(context.getType(reference.get()));
                }
            }
            // ... or a function
            ExpressionFunction func = context.getFunction(source);
            if (func != null) {
                return Optional.of(func.getBody().type(context));
            }
        }
        return Optional.empty();  // if this context does not contain this input
    }

    private static class AttributeErrorType extends TensorType {
        private final DeployLogger deployLogger;
        private final String attr;
        private final Attribute.CollectionType collType;
        private boolean shouldWarn = true;
        AttributeErrorType(DeployLogger deployLogger, String attr, Attribute.CollectionType collType) {
            super(TensorType.Value.DOUBLE, List.of());
            this.deployLogger = deployLogger;
            this.attr = attr;
            this.collType = collType;
        }
        private void warnOnce() {
            if (shouldWarn) {
                deployLogger.log(Level.WARNING, "Using attribute(" + attr +") " + collType + " in ranking expression will always evaluate to 0.0");
                shouldWarn = false;
            }
        }
        @Override public TensorType.Value valueType() { warnOnce(); return super.valueType(); }
        @Override public int rank() { warnOnce(); return super.rank(); }
        @Override public List<TensorType.Dimension> dimensions() { warnOnce(); return super.dimensions(); }
        @Override public boolean equals(Object o) {
            if (o instanceof TensorType other) {
                return (other.rank() == 0);
            }
            return false;
        }
    }

    private void addAttributeFeatureTypes(ImmutableSDField field, Map<Reference, TensorType> featureTypes) {
        Attribute attribute = field.getAttribute();
        field.getAttributes().forEach((k, a) -> {
            String name = k;
            if (attribute == a)                              // this attribute should take the fields name
                name = field.getName();                      // switch to that - it is separate for imported fields
            if (a.getCollectionType().equals(Attribute.CollectionType.SINGLE)) {
                featureTypes.put(FeatureNames.asAttributeFeature(name), a.tensorType().orElse(TensorType.empty));
            } else {
                featureTypes.put(FeatureNames.asAttributeFeature(name), new AttributeErrorType(deployLogger, name, a.getCollectionType()));
            }
        });
    }

    @Override
    public String toString() {
        return "rank profile '" + name() + "'";
    }

    /**
     * A rank setting. The identity of a rank setting is its field name and type (not value).
     * A rank setting is immutable.
     */
    public static class RankSetting {

        private final String fieldName;

        private final Type type;

        /** The rank value */
        private final Object value;

        public enum Type {

            RANKTYPE("rank-type"),
            LITERALBOOST("literal-boost"),
            WEIGHT("weight"),
            PREFERBITVECTOR("preferbitvector",true);

            private final String name;

            /** True if this setting really pertains to an index, not a field within an index */
            private final boolean isIndexLevel;

            Type(String name) {
                this(name,false);
            }

            Type(String name,boolean isIndexLevel) {
                this.name = name;
                this.isIndexLevel=isIndexLevel;
            }

            /** True if this setting really pertains to an index, not a field within an index */
            public boolean isIndexLevel() { return isIndexLevel; }

            /** Returns the name of this type */
            public String getName() {
                return name;
            }

            @Override
            public String toString() {
                return "type " + name;
            }

        }

        public RankSetting(String fieldName, RankSetting.Type type, Object value) {
            this.fieldName = fieldName;
            this.type = type;
            this.value = value;
        }

        public String getFieldName() { return fieldName; }

        public Type getType() { return type; }

        public Object getValue() { return value; }

        /** Returns the value as an int, or a negative value if it is not an integer */
        public int getIntValue() {
            if (value instanceof Integer) {
                return ((Integer)value);
            }
            else {
                return -1;
            }
        }

        @Override
        public int hashCode() {
            return fieldName.hashCode() + 17 * type.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof RankSetting other)) {
                return false;
            }
            return fieldName.equals(other.fieldName) && type.equals(other.type);
        }

        @Override
        public String toString() {
            return type + " setting " + fieldName + ": " + value;
        }

    }

    /** A rank property. Rank properties are Value Objects */
    public static class RankProperty {

        private final String name;
        private final String value;

        public RankProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }

        public String getValue() { return value; }

        @Override
        public int hashCode() {
            return name.hashCode() + 17 * value.hashCode();
        }

        @Override
        public boolean equals(Object object) {
            if (! (object instanceof RankProperty other)) return false;
            return (other.name.equals(this.name) && other.value.equals(this.value));
        }

        @Override
        public String toString() {
            return name + " = " + value;
        }

    }

    /** A function in a rank profile */
    public static class RankingExpressionFunction {

        private ExpressionFunction function;

        /** True if this should be inlined into calling expressions. Useful for very cheap functions. */
        private final boolean inline;

        RankingExpressionFunction(ExpressionFunction function, boolean inline) {
            this.function = function;
            this.inline = inline;
        }

        public void setReturnType(TensorType type) {
            this.function = function.withReturnType(type);
        }

        public ExpressionFunction function() { return function; }

        public boolean inline() {
            return inline && function.arguments().isEmpty(); // only inline no-arg functions;
        }

        RankingExpressionFunction withExpression(RankingExpression expression) {
            return new RankingExpressionFunction(function.withBody(expression), inline);
        }

        @Override
        public String toString() {
            return function.toString();
        }

    }

    public static final class DiversitySettings {

        private String attribute = null;
        private int minGroups = 0;
        private double cutoffFactor = 10;
        private Diversity.CutoffStrategy cutoffStrategy = Diversity.CutoffStrategy.loose;

        public void setAttribute(String value) { attribute = value; }
        public void setMinGroups(int value) { minGroups = value; }
        public void setCutoffFactor(double value) { cutoffFactor = value; }
        public void setCutoffStrategy(Diversity.CutoffStrategy strategy) { cutoffStrategy = strategy; }
        public String getAttribute() { return attribute; }
        public int getMinGroups() { return minGroups; }
        public double getCutoffFactor() { return cutoffFactor; }
        public Diversity.CutoffStrategy getCutoffStrategy() { return cutoffStrategy; }

        void checkValid() {
            if (attribute == null || attribute.isEmpty()) {
                throw new IllegalArgumentException("'diversity' did not set non-empty diversity attribute name.");
            }
            if (minGroups <= 0) {
                throw new IllegalArgumentException("'diversity' did not set min-groups > 0");
            }
            if (cutoffFactor < 1.0) {
                throw new IllegalArgumentException("diversity.cutoff.factor must be larger or equal to 1.0.");
            }
        }
    }

    public static class MatchPhaseSettings {

        private String attribute = null;
        private boolean ascending = false;
        private int maxHits = 0; // try to get this many hits before degrading the match phase
        private double maxFilterCoverage = 0.2; // Max coverage of original corpus that will trigger the filter.
        private double evaluationPoint = 0.20;
        private double prePostFilterTippingPoint = 1.0;

        public void setAscending(boolean value) { ascending = value; }
        public void setAttribute(String value) { attribute = value; }
        public void setMaxHits(int value) { maxHits = value; }
        public void setMaxFilterCoverage(double value) { maxFilterCoverage = value; }
        public void setEvaluationPoint(double evaluationPoint) { this.evaluationPoint = evaluationPoint; }
        public void setPrePostFilterTippingPoint(double prePostFilterTippingPoint) { this.prePostFilterTippingPoint = prePostFilterTippingPoint; }

        public boolean                getAscending() { return ascending; }
        public String                 getAttribute() { return attribute; }
        public int                      getMaxHits() { return maxHits; }
        public double         getMaxFilterCoverage() { return maxFilterCoverage; }
        public double           getEvaluationPoint() { return evaluationPoint; }
        public double getPrePostFilterTippingPoint() { return prePostFilterTippingPoint; }

        public void checkValid() {
            if (attribute == null) {
                throw new IllegalArgumentException("match-phase did not set any attribute");
            }
            if (! (maxHits > 0)) {
                throw new IllegalArgumentException("match-phase did not set max-hits > 0");
            }
        }

    }

    public static class TypeSettings {

        private final Map<String, String> types = new HashMap<>();

        void addType(String name, String type) {
            types.put(name, type);
        }

        public Map<String, String> getTypes() {
            return Collections.unmodifiableMap(types);
        }

    }

    public static final class Input {

        private final Reference name;
        private final InputType type;
        private final Optional<Tensor> defaultValue;

        public Input(Reference name, InputType type, Optional<Tensor> defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        public Input(Reference name, TensorType tType, Optional<Tensor> defaultValue) {
            this(name, new InputType(tType, false), defaultValue);
        }

        public Reference name() { return name; }
        public InputType type() { return type; }
        public Optional<Tensor> defaultValue() { return defaultValue; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Input other)) return false;
            if ( ! other.name().equals(this.name())) return false;
            if ( ! other.type().equals(this.type())) return false;
            if ( ! other.defaultValue().equals(this.defaultValue())) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, defaultValue);
        }

        @Override
        public String toString() {
            return "input " + name + " " + type +
                   (defaultValue().isPresent() ? ":" + defaultValue.get().toAbbreviatedString(false, true) : "");
        }

    }

    public static final class Constant {

        private final Reference name;
        private final TensorType type;

        // One of these are non-empty
        private final Optional<Tensor> value;
        private final Optional<String> valuePath;

        // Always set only if valuePath is set
        private final Optional<DistributableResource.PathType> pathType;

        public Constant(Reference name, Tensor value) {
            this(name, value.type(), Optional.of(value), Optional.empty(), Optional.empty());
        }

        public Constant(Reference name, TensorType type, String valuePath) {
            this(name, type, Optional.empty(), Optional.of(valuePath), Optional.of(DistributableResource.PathType.FILE));
        }

        public Constant(Reference name, TensorType type, String valuePath, DistributableResource.PathType pathType) {
            this(name, type, Optional.empty(), Optional.of(valuePath), Optional.of(pathType));
        }

        private Constant(Reference name, TensorType type, Optional<Tensor> value,
                         Optional<String> valuePath,  Optional<DistributableResource.PathType> pathType) {
            this.name = Objects.requireNonNull(name);
            this.type = Objects.requireNonNull(type);
            this.value = Objects.requireNonNull(value);
            this.valuePath = Objects.requireNonNull(valuePath);
            this.pathType = Objects.requireNonNull(pathType);

            if (type.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
                throw new IllegalArgumentException("Illegal type of constant " + name + " type " + type +
                                                   ": Dense tensor dimensions must have a size");
        }

        public Reference name() { return name; }
        public TensorType type() { return type; }

        /** Returns the value of this, if its path is empty. */
        public Optional<Tensor> value() { return value; }

        /** Returns the path to the value of this, if its value is empty. */
        public Optional<String> valuePath() { return valuePath; }

        /** Returns the path type, if valuePath is set. */
        public Optional<DistributableResource.PathType> pathType() { return pathType; }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if ( ! (o instanceof Constant other)) return false;
            if ( ! other.name().equals(this.name())) return false;
            if ( ! other.type().equals(this.type())) return false;
            if ( ! other.value().equals(this.value())) return false;
            if ( ! other.valuePath().equals(this.valuePath())) return false;
            if ( ! other.pathType().equals(this.pathType())) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, value, valuePath, pathType);
        }

        @Override
        public String toString() {
            return "constant '" + name + "' " + type + ":" +
                   (value().isPresent() ? value.get().toAbbreviatedString() : " file:" + valuePath.get());
        }

    }

    private static class CachedFunctions {

        private final Map<String, RankingExpressionFunction> allRankingExpressionFunctions;

        private final ImmutableMap<String, ExpressionFunction> allExpressionFunctions;

        CachedFunctions(Map<String, RankingExpressionFunction> functions) {
            allRankingExpressionFunctions = functions;
            ImmutableMap.Builder<String,ExpressionFunction> mapBuilder = new ImmutableMap.Builder<>();
            for (var entry : functions.entrySet()) {
                ExpressionFunction function = entry.getValue().function();
                mapBuilder.put(function.getName(), function);
            }
            allExpressionFunctions = mapBuilder.build();
        }

    }

    public record RankFeatureNormalizer(Reference original, String name, String input, String algo, double kparam) {
        @Override
        public String toString() {
            return "normalizer{name=" + name + ",input=" + input + ",algo=" + algo + ",k=" + kparam + "}";
        }
        private static long hash(String s) {
            int bob = com.yahoo.collections.BobHash.hash(s);
            return bob + 0x100000000L;
        }
        public static RankFeatureNormalizer linear(Reference original, Reference inputRef) {
            long h = hash(original.toString());
            String name = "normalize@" + h + "@linear";
            return new RankFeatureNormalizer(original, name, inputRef.toString(), "LINEAR", 0.0);
        }
        public static RankFeatureNormalizer rrank(Reference original, Reference inputRef, double k) {
            long h = hash(original.toString());
            String name = "normalize@" + h + "@rrank";
            return new RankFeatureNormalizer(original, name, inputRef.toString(), "RRANK", k);
        }
    }

    private final List<RankFeatureNormalizer> featureNormalizers = new ArrayList<>();

    public Map<String, RankFeatureNormalizer> getFeatureNormalizers() {
        Map<String, RankFeatureNormalizer> all = new LinkedHashMap<>();
        for (var inheritedProfile : inherited()) {
            all.putAll(inheritedProfile.getFeatureNormalizers());
        }
        // Use a copy to avoid concurrent modification exceptions, see addFeatureNormalizer() below
        for (var n : new ArrayList<>(featureNormalizers)) {
            all.put(n.name(), n);
        }
        return all;
    }

    public void addFeatureNormalizer(RankFeatureNormalizer n) {
        if (functions.get(n.name()) != null) {
            throw new IllegalArgumentException("cannot use name '" + name + "' for both function and normalizer");
        }
        featureNormalizers.add(n);
    }

    private void verifyNoNormalizers(String where, RankingExpressionFunction f, Map<String, RankFeatureNormalizer> allNormalizers, RankProfileTransformContext context) {
        if (f == null) return;
        verifyNoNormalizers(where, f.function(), allNormalizers, context);
    }

    private void verifyNoNormalizers(String where, ExpressionFunction func, Map<String, RankFeatureNormalizer> allNormalizers, RankProfileTransformContext context) {
        if (func == null) return;
        var body = func.getBody();
        if (body == null) return;
        verifyNoNormalizers(where, body.getRoot(), allNormalizers, context);
    }

    private void verifyNoNormalizers(String where, ExpressionNode node, Map<String, RankFeatureNormalizer> allNormalizers, RankProfileTransformContext context) {
        var needInputs = new HashSet<String>();
        var recorder = new InputRecorder(needInputs);
        recorder.process(node, context);
        for (var input : needInputs) {
            var normalizer = allNormalizers.get(input);
            if (normalizer != null) {
                throw new IllegalArgumentException("Cannot use " + normalizer.original() + " from " + where + ", only valid in global-phase expression");
            }
        }
    }

    private OptionalInt asOptionalInt(Optional<Integer> v) {
        if (v.isEmpty()) return OptionalInt.empty();
        return OptionalInt.of(v.get());
    }

}
