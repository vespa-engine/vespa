// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import com.google.common.collect.ImmutableMap;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.ImmutableSDField;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.expressiontransforms.ExpressionTransforms;
import com.yahoo.searchdefinition.expressiontransforms.RankProfileTransformContext;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.Arguments;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
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
import java.util.Set;
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
    /** The search definition-unique name of this rank profile */
    private final String name;

    /** The search definition owning this profile, or null if global (owned by a model) */
    private final ImmutableSearch search;

    /** The name of the rank profile inherited by this */
    private String inheritedName = null;
    private RankProfile inherited = null;

    /** The match settings of this profile */
    private MatchPhaseSettings matchPhaseSettings = null;

    /** The rank settings of this profile */
    protected Set<RankSetting> rankSettings = new java.util.LinkedHashSet<>();

    /** The ranking expression to be used for first phase */
    private RankingExpressionFunction firstPhaseRanking = null;

    /** The ranking expression to be used for second phase */
    private RankingExpressionFunction secondPhaseRanking = null;

    /** Number of hits to be reranked in second phase, -1 means use default */
    private int rerankCount = -1;

    /** Mysterious attribute */
    private int keepRankCount = -1;

    private int numThreadsPerSearch = -1;
    private int minHitsPerThread = -1;
    private int numSearchPartitions = -1;

    private Double termwiseLimit = null;

    /** The drop limit used to drop hits with rank score less than or equal to this value */
    private double rankScoreDropLimit = -Double.MAX_VALUE;

    private Set<ReferenceNode> summaryFeatures;
    private String inheritedSummaryFeatures;

    private Set<ReferenceNode> rankFeatures;

    /** The properties of this - a multimap */
    private Map<String, List<RankProperty>> rankProperties = new LinkedHashMap<>();

    private Boolean ignoreDefaultRankFeatures = null;

    private Map<String, RankingExpressionFunction> functions = new LinkedHashMap<>();
    // This cache must be invalidated every time modifications are done to 'functions'.
    private CachedFunctions allFunctionsCached = null;

    private Map<Reference, TensorType> inputFeatures = new LinkedHashMap<>();

    private Set<String> filterFields = new HashSet<>();

    private final RankProfileRegistry rankProfileRegistry;

    /** Constants in ranking expressions */
    private Map<String, Value> constants = new HashMap<>();

    private final TypeSettings attributeTypes = new TypeSettings();

    private final TypeSettings queryFeatureTypes = new TypeSettings();

    private List<ImmutableSDField> allFieldsList;

    /** Global onnx models not tied to a search definition */
    private final OnnxModels onnxModels;
    private final RankingConstants rankingConstants;
    private final ApplicationPackage applicationPackage;
    private final DeployLogger deployLogger;

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

    /**
     * Creates a new rank profile for a particular search definition
     *
     * @param name                the name of the new profile
     * @param search              the search definition owning this profile
     * @param rankProfileRegistry the {@link com.yahoo.searchdefinition.RankProfileRegistry} to use for storing
     *                            and looking up rank profiles.
     */
    public RankProfile(String name, Search search, RankProfileRegistry rankProfileRegistry, RankingConstants rankingConstants) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.search = Objects.requireNonNull(search, "search cannot be null");
        this.onnxModels = null;
        this.rankingConstants = rankingConstants;
        this.rankProfileRegistry = rankProfileRegistry;
        this.applicationPackage = search.applicationPackage();
        this.deployLogger = search.getDeployLogger();
    }

    /**
     * Creates a global rank profile
     *
     * @param name  the name of the new profile
     */
    public RankProfile(String name, ApplicationPackage applicationPackage, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, RankingConstants rankingConstants, OnnxModels onnxModels) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.search = null;
        this.rankProfileRegistry = rankProfileRegistry;
        this.rankingConstants = rankingConstants;
        this.onnxModels = onnxModels;
        this.applicationPackage = applicationPackage;
        this.deployLogger = deployLogger;
    }

    public String getName() { return name; }

    /** Returns the search definition owning this, or null if it is global */
    public ImmutableSearch getSearch() { return search; }

    /** Returns the application this is part of */
    public ApplicationPackage applicationPackage() {
        return applicationPackage;
    }

    /** Returns the ranking constants of the owner of this */
    public RankingConstants rankingConstants() {
        return rankingConstants;
    }

    public Map<String, OnnxModel> onnxModels() {
        return search != null ? search.onnxModels().asMap() : onnxModels.asMap();
    }

    private Stream<ImmutableSDField> allFields() {
        if (search == null) return Stream.empty();
        if (allFieldsList == null) {
            allFieldsList = search.allFieldsList();
        }
        return allFieldsList.stream();
    }

    private Stream<ImmutableSDField> allImportedFields() {
        return search != null ? search.allImportedFields() : Stream.empty();
    }

    /**
     * Sets the name of the rank profile this inherits. Both rank profiles must be present in the same search
     * definition
     */
    public void setInherited(String inheritedName) {
        this.inheritedName = inheritedName;
    }

    /** Returns the name of the profile this one inherits, or null if none is inherited */
    public String getInheritedName() { return inheritedName; }

    /** Returns the inherited rank profile, or null if there is none */
    private RankProfile getInherited() {
        if (inheritedName == null) return null;
        if (inherited == null) {
            inherited = resolveInherited();
            if (inherited == null) {
                String msg = "rank-profile '" + getName() + "' inherits '" + inheritedName +
                        "', but it does not exist anywhere in the inheritance of search '" +
                        ((getSearch() != null) ? getSearch().getName() : " global rank profiles") + "'.";
                throw new IllegalArgumentException(msg);
            } else {
                List<String> children = new ArrayList<>();
                children.add(createFullyQualifiedName());
                verifyNoInheritanceCycle(children, inherited);
            }
        }
        return inherited;
    }

    private String createFullyQualifiedName() {
        return (search != null)
                ? (search.getName() + "." + getName())
                : getName();
    }

    private void verifyNoInheritanceCycle(List<String> children, RankProfile parent) {
        children.add(parent.createFullyQualifiedName());
        String root = children.get(0);
        if (root.equals(parent.createFullyQualifiedName())) {
            throw new IllegalArgumentException("There is a cycle in the inheritance for rank-profile '" + root + "' = " + children);
        }
        if (parent.getInherited() != null) {
            verifyNoInheritanceCycle(children, parent.getInherited());
        }
    }

    private RankProfile resolveInherited(ImmutableSearch search) {
        SDDocumentType documentType = search.getDocument();
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
        return rankProfileRegistry.get(search.getName(), inheritedName);
    }

    private RankProfile resolveInherited() {
        if (inheritedName == null) return null;
        return (getSearch() != null)
                ? resolveInherited(search)
                : rankProfileRegistry.getGlobal(inheritedName);
    }

    /**
     * Returns whether this profile inherits (directly or indirectly) the given profile
     *
     * @param name the profile name to compare this to.
     * @return whether or not this inherits from the named profile.
     */
    public boolean inherits(String name) {
        RankProfile parent = getInherited();
        while (parent != null) {
            if (parent.getName().equals(name))
                return true;
            parent = parent.getInherited();
        }
        return false;
    }

    public void setMatchPhaseSettings(MatchPhaseSettings settings) {
        settings.checkValid();
        this.matchPhaseSettings = settings;
    }

    public MatchPhaseSettings getMatchPhaseSettings() {
        MatchPhaseSettings settings = this.matchPhaseSettings;
        if (settings != null) return settings;
        if (getInherited() != null) return getInherited().getMatchPhaseSettings();
        return null;
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
     * @param field the field whose settings to return.
     * @param type  the type that the field is required to be.
     * @return the rank setting found, or null.
     */
    RankSetting getDeclaredRankSetting(String field, RankSetting.Type type) {
        for (Iterator<RankSetting> i = declaredRankSettingIterator(); i.hasNext(); ) {
            RankSetting setting = i.next();
            if (setting.getFieldName().equals(field) &&
                setting.getType().equals(type)) {
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

        if (getInherited() != null) return getInherited().getRankSetting(field, type);

        return null;
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
        Set<RankSetting> allSettings = new LinkedHashSet<>(rankSettings);
        RankProfile parent = getInherited();
        if (parent != null)
            allSettings.addAll(parent.rankSettings());

        return allSettings;
    }

    public void addConstant(String name, Value value) {
        if (value instanceof TensorValue) {
            TensorType type = value.type();
            if (type.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
                throw new IllegalArgumentException("Illegal type of constant " + name + " type " + type +
                                                   ": Dense tensor dimensions must have a size");
        }
        constants.put(name, value.freeze());
    }

    public void addConstantTensor(String name, TensorValue value) {
        addConstant(name, value);
    }

    /** Returns an unmodifiable view of the constants available in this */
    public Map<String, Value> getConstants() {
        if (constants.isEmpty())
            return getInherited() != null ? getInherited().getConstants() : Collections.emptyMap();
        if (getInherited() == null || getInherited().getConstants().isEmpty())
            return Collections.unmodifiableMap(constants);

        Map<String, Value> combinedConstants = new HashMap<>(getInherited().getConstants());
        combinedConstants.putAll(constants);
        return combinedConstants;
    }

    public void addAttributeType(String attributeName, String attributeType) {
        attributeTypes.addType(attributeName, attributeType);
    }

    public Map<String, String> getAttributeTypes() {
        return attributeTypes.getTypes();
    }

    public void addQueryFeatureType(String queryFeature, String queryFeatureType) {
        queryFeatureTypes.addType(queryFeature, queryFeatureType);
    }

    public Map<String, String> getQueryFeatureTypes() {
        return queryFeatureTypes.getTypes();
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
        RankProfile inherited = getInherited();
        if (inherited != null) return inherited.getFirstPhase();
        return null;
    }

    void setFirstPhaseRanking(RankingExpression rankingExpression) {
        this.firstPhaseRanking = new RankingExpressionFunction(new ExpressionFunction(FIRST_PHASE, Collections.emptyList(), rankingExpression), false);
    }

    public void setFirstPhaseRanking(String expression) {
        try {
            firstPhaseRanking = new RankingExpressionFunction(parseRankingExpression(FIRST_PHASE, Collections.emptyList(), expression), false);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Illegal first phase ranking function", e);
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
        RankProfile inherited = getInherited();
        if (inherited != null) return inherited.getSecondPhase();
        return null;
    }

    public void setSecondPhaseRanking(String expression) {
        try {
            secondPhaseRanking = new RankingExpressionFunction(parseRankingExpression(SECOND_PHASE, Collections.emptyList(), expression), false);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Illegal second phase ranking function", e);
        }
    }

    /** Returns a read-only view of the summary features to use in this profile. This is never null */
    public Set<ReferenceNode> getSummaryFeatures() {
        if (inheritedSummaryFeatures != null && summaryFeatures != null) {
            Set<ReferenceNode> combined = new HashSet<>();
            combined.addAll(getInherited().getSummaryFeatures());
            combined.addAll(summaryFeatures);
            return Collections.unmodifiableSet(combined);
        }
        if (summaryFeatures != null) return Collections.unmodifiableSet(summaryFeatures);
        if (getInherited() != null) return getInherited().getSummaryFeatures();
        return Set.of();
    }

    private void addSummaryFeature(ReferenceNode feature) {
        if (summaryFeatures == null)
            summaryFeatures = new LinkedHashSet<>();
        summaryFeatures.add(feature);
    }

    /** Adds the content of the given feature list to the internal list of summary features. */
    public void addSummaryFeatures(FeatureList features) {
        for (ReferenceNode feature : features) {
            addSummaryFeature(feature);
        }
    }

    /**
     * Sets the name this should inherit the summary features of.
     * Without setting this, this will either have the summary features of the parent,
     * or if summary features are set in this, only have the summary features in this.
     * With this set the resulting summary features of this will be the superset of those defined in this and
     * the final (with inheritance included) summary features of the given parent.
     * The profile must be the profile which is directly inherited by this.
     *
     */
    public void setInheritedSummaryFeatures(String parentProfile) {
        if ( ! parentProfile.equals(inheritedName))
            throw new IllegalArgumentException("This can only inherit the summary features of its parent, '" +
                                               inheritedName + ", but attemtping to inherit '" + parentProfile);
        this.inheritedSummaryFeatures = parentProfile;
    }

    /** Returns a read-only view of the rank features to use in this profile. This is never null */
    public Set<ReferenceNode> getRankFeatures() {
        if (rankFeatures != null) return Collections.unmodifiableSet(rankFeatures);
        if (getInherited() != null) return getInherited().getRankFeatures();
        return Collections.emptySet();
    }

    private void addRankFeature(ReferenceNode feature) {
        if (rankFeatures == null)
            rankFeatures = new LinkedHashSet<>();
        rankFeatures.add(feature);
    }

    /**
     * Adds the content of the given feature list to the internal list of rank features.
     *
     * @param features The features to add.
     */
    public void addRankFeatures(FeatureList features) {
        for (ReferenceNode feature : features) {
            addRankFeature(feature);
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
        if (rankProperties.size() == 0 && getInherited() == null) return Collections.emptyMap();
        if (rankProperties.size() == 0) return getInherited().getRankPropertyMap();
        if (getInherited() == null) return Collections.unmodifiableMap(rankProperties);

        // Neither is null
        Map<String, List<RankProperty>> combined = new LinkedHashMap<>(getInherited().getRankPropertyMap());
        combined.putAll(rankProperties); // Don't combine values across inherited properties
        return Collections.unmodifiableMap(combined);
    }

    public void addRankProperty(String name, String parameter) {
        addRankProperty(new RankProperty(name, parameter));
    }

    private void addRankProperty(RankProperty rankProperty) {
        // Just the usual multimap semantics here
        rankProperties.computeIfAbsent(rankProperty.getName(), (String key) -> new ArrayList<>(1)).add(rankProperty);
    }

    @Override
    public String toString() {
        return "rank profile '" + getName() + "'";
    }

    public int getRerankCount() {
        return (rerankCount < 0 && (getInherited() != null))
                ?  getInherited().getRerankCount()
                : rerankCount;
    }

    public int getNumThreadsPerSearch() {
        return (numThreadsPerSearch < 0 && (getInherited() != null))
                ?  getInherited().getNumThreadsPerSearch()
                : numThreadsPerSearch;
    }

    public void setNumThreadsPerSearch(int numThreads) {
        this.numThreadsPerSearch = numThreads;
    }

    public int getMinHitsPerThread() {
        return (minHitsPerThread < 0 && (getInherited() != null))
                ?  getInherited().getMinHitsPerThread()
                : minHitsPerThread;
    }

    public void setMinHitsPerThread(int minHits) {
        this.minHitsPerThread = minHits;
    }

    public void setNumSearchPartitions(int numSearchPartitions) {
        this.numSearchPartitions = numSearchPartitions;
    }

    public int getNumSearchPartitions() {
        return (numSearchPartitions < 0 && (getInherited() != null))
                ?  getInherited().getNumSearchPartitions()
                : numSearchPartitions;
    }

    public OptionalDouble getTermwiseLimit() {
        return ((termwiseLimit == null) && (getInherited() != null))
                ?  getInherited().getTermwiseLimit()
                : (termwiseLimit != null) ? OptionalDouble.of(termwiseLimit) : OptionalDouble.empty();
    }
    public void setTermwiseLimit(double termwiseLimit) { this.termwiseLimit = termwiseLimit; }

    /** Sets the rerank count. Set to -1 to use inherited */
    public void setRerankCount(int rerankCount) {
        this.rerankCount = rerankCount;
    }

    /** Whether we should ignore the default rank features. Set to null to use inherited */
    public void setIgnoreDefaultRankFeatures(Boolean ignoreDefaultRankFeatures) {
        this.ignoreDefaultRankFeatures = ignoreDefaultRankFeatures;
    }

    public boolean getIgnoreDefaultRankFeatures() {
        if (ignoreDefaultRankFeatures != null) return ignoreDefaultRankFeatures;
        return (getInherited() != null) && getInherited().getIgnoreDefaultRankFeatures();
    }

    /** Adds a function */
    public void addFunction(String name, List<String> arguments, String expression, boolean inline) {
        try {
            addFunction(parseRankingExpression(name, arguments, expression), inline);
        }
        catch (ParseException e) {
            throw new IllegalArgumentException("Could not parse function '" + name + "'", e);
        }
    }

    /** Adds a function and returns it */
    public RankingExpressionFunction addFunction(ExpressionFunction function, boolean inline) {
        RankingExpressionFunction rankingExpressionFunction = new RankingExpressionFunction(function, inline);
        if (functions.containsKey(function.getName())) {
            deployLogger.log(Level.WARNING, "Function '" + function.getName() + "' replaces a previous function " +
                    "with the same name in rank profile '" + this.name + "'");
        }
        functions.put(function.getName(), rankingExpressionFunction);
        allFunctionsCached = null;
        return rankingExpressionFunction;
    }

    /**
     * Use for rank profiles representing a model evaluation; it will assume
     * that a input is provided with the declared type (for the purpose of
     * type resolving).
     **/
    public void addInputFeature(String name, TensorType declaredType) {
        Reference ref = Reference.fromIdentifier(name);
        if (inputFeatures.containsKey(ref)) {
            TensorType hadType = inputFeatures.get(ref);
            if (! declaredType.equals(hadType)) {
                throw new IllegalArgumentException("Tried to replace input feature "+name+" with different type: "+
                                                   hadType+" -> "+declaredType);
            }
        }
        inputFeatures.put(ref, declaredType);
    }

    public RankingExpressionFunction findFunction(String name) {
        RankingExpressionFunction function = functions.get(name);
        return ((function == null) && (getInherited() != null))
                ? getInherited().findFunction(name)
                : function;
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

    private  Map<String, RankingExpressionFunction> gatherAllFunctions() {
        if (functions.isEmpty() && getInherited() == null) return Collections.emptyMap();
        if (functions.isEmpty()) return getInherited().getFunctions();
        if (getInherited() == null) return Collections.unmodifiableMap(new LinkedHashMap<>(functions));

        // Neither is null
        Map<String, RankingExpressionFunction> allFunctions = new LinkedHashMap<>(getInherited().getFunctions());
        allFunctions.putAll(functions);
        return Collections.unmodifiableMap(allFunctions);
    }

    private boolean needToUpdateFunctionCache() {
        if (getInherited() != null)
            return (allFunctionsCached == null) || getInherited().needToUpdateFunctionCache();
        return allFunctionsCached == null;
    }

    public int getKeepRankCount() {
        if (keepRankCount >= 0) return keepRankCount;
        if (getInherited() != null) return getInherited().getKeepRankCount();
        return -1;
    }

    public void setKeepRankCount(int rerankArraySize) {
        this.keepRankCount = rerankArraySize;
    }

    public double getRankScoreDropLimit() {
        if (rankScoreDropLimit >- Double.MAX_VALUE) return rankScoreDropLimit;
        if (getInherited() != null) return getInherited().getRankScoreDropLimit();
        return rankScoreDropLimit;
    }

    public void setRankScoreDropLimit(double rankScoreDropLimit) {
        this.rankScoreDropLimit = rankScoreDropLimit;
    }

    public Set<String> filterFields() {
        return filterFields;
    }

    /**
     * Returns all filter fields in this profile and any profile it inherits.
     *
     * @return the set of all filter fields
     */
    public Set<String> allFilterFields() {
        RankProfile parent = getInherited();
        Set<String> retval = new LinkedHashSet<>();
        if (parent != null) {
            retval.addAll(parent.allFilterFields());
        }
        retval.addAll(filterFields());
        return retval;
    }

    private ExpressionFunction parseRankingExpression(String name, List<String> arguments, String expression) throws ParseException {
        if (expression.trim().length() == 0)
            throw new ParseException("Encountered an empty ranking expression in " + getName()+ ", " + name + ".");

        try (Reader rankingExpressionReader = openRankingExpressionReader(name, expression.trim())) {
            return new ExpressionFunction(name, arguments, new RankingExpression(name, rankingExpressionReader));
        }
        catch (com.yahoo.searchlib.rankingexpression.parser.ParseException e) {
            ParseException exception = new ParseException("Could not parse ranking expression '" + expression.trim() +
                                                          "' in " + getName()+ ", " + name + ".");
            throw (ParseException)exception.initCause(e);
        }
        catch (IOException e) {
            throw new RuntimeException("IOException parsing ranking expression '" + name + "'");
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
        File file = new File(fileName);
        if (!file.isAbsolute() && file.getPath().contains("/")) // See ticket 4102122
            throw new IllegalArgumentException("In " + getName() + ", " + expName + ", ranking references file '" + file +
                    "' in subdirectory, which is not supported.");

        return search.getRankingExpression(fileName);
    }

    /** Shallow clones this */
    @Override
    public RankProfile clone() {
        try {
            RankProfile clone = (RankProfile)super.clone();
            clone.rankSettings = new LinkedHashSet<>(this.rankSettings);
            clone.matchPhaseSettings = this.matchPhaseSettings; // hmm?
            clone.summaryFeatures = summaryFeatures != null ? new LinkedHashSet<>(this.summaryFeatures) : null;
            clone.rankFeatures = rankFeatures != null ? new LinkedHashSet<>(this.rankFeatures) : null;
            clone.rankProperties = new LinkedHashMap<>(this.rankProperties);
            clone.inputFeatures = new LinkedHashMap<>(this.inputFeatures);
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
            throw new IllegalArgumentException("Rank profile '" + getName() + "' is invalid", e);
        }
    }

    private void compileThis(QueryProfileRegistry queryProfiles, ImportedMlModels importedModels) {
        checkNameCollisions(getFunctions(), getConstants());
        ExpressionTransforms expressionTransforms = new ExpressionTransforms();

        Map<Reference, TensorType> featureTypes = collectFeatureTypes();
        // Function compiling first pass: compile inline functions without resolving other functions
        Map<String, RankingExpressionFunction> inlineFunctions =
                compileFunctions(this::getInlineFunctions, queryProfiles, featureTypes, importedModels, Collections.emptyMap(), expressionTransforms);

        firstPhaseRanking = compile(this.getFirstPhase(), queryProfiles, featureTypes, importedModels, getConstants(), inlineFunctions, expressionTransforms);
        secondPhaseRanking = compile(this.getSecondPhase(), queryProfiles, featureTypes, importedModels, getConstants(), inlineFunctions, expressionTransforms);

        // Function compiling second pass: compile all functions and insert previously compiled inline functions
        // TODO This merges all functions from inherited profiles too and erases inheritance information. Not good.
        functions = compileFunctions(this::getFunctions, queryProfiles, featureTypes, importedModels, inlineFunctions, expressionTransforms);
        allFunctionsCached = null;
    }

    private void checkNameCollisions(Map<String, RankingExpressionFunction> functions, Map<String, Value> constants) {
        for (Map.Entry<String, RankingExpressionFunction> functionEntry : functions.entrySet()) {
            if (constants.containsKey(functionEntry.getKey()))
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
        // Because some functions (imported models adding generated macros) may add other functions during compiling.
        // A straightforward iteration will either miss those functions, or may cause a ConcurrentModificationException
        while (null != (entry = findUncompiledFunction(functions.get(), compiledFunctions.keySet()))) {
            RankingExpressionFunction rankingExpressionFunction = entry.getValue();
            RankingExpressionFunction compiled = compile(rankingExpressionFunction, queryProfiles, featureTypes,
                                                 importedModels, getConstants(), inlineFunctions, expressionTransforms);
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
                                      Map<String, Value> constants,
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
            addRankProperty(rankProperty.getKey(), rankProperty.getValue());
        }
        return function.withExpression(expression);
    }

    /**
     * Creates a context containing the type information of all constants, attributes and query profiles
     * referable from this rank profile.
     */
    public MapEvaluationTypeContext typeContext(QueryProfileRegistry queryProfiles) {
        return typeContext(queryProfiles, collectFeatureTypes());
    }

    public MapEvaluationTypeContext typeContext() { return typeContext(new QueryProfileRegistry()); }

    private Map<Reference, TensorType> collectFeatureTypes() {
        Map<Reference, TensorType> featureTypes = new HashMap<>();
        // Add input features
        inputFeatures.forEach((k, v) -> featureTypes.put(k, v));
        // Add attributes
        allFields().forEach(field -> addAttributeFeatureTypes(field, featureTypes));
        allImportedFields().forEach(field -> addAttributeFeatureTypes(field, featureTypes));
        return featureTypes;
    }
    
    public MapEvaluationTypeContext typeContext(QueryProfileRegistry queryProfiles, Map<Reference, TensorType> featureTypes) {
        MapEvaluationTypeContext context = new MapEvaluationTypeContext(getExpressionFunctions(), featureTypes);

        // Add small and large constants, respectively
        getConstants().forEach((k, v) -> context.setType(FeatureNames.asConstantFeature(k), v.type()));
        rankingConstants().asMap().forEach((k, v) -> context.setType(FeatureNames.asConstantFeature(k), v.getTensorType()));

        // Add query features from all rank profile types
        for (QueryProfileType queryProfileType : queryProfiles.getTypeRegistry().allComponents()) {
            for (FieldDescription field : queryProfileType.declaredFields().values()) {
                TensorType type = field.getType().asTensorType();
                Optional<Reference> feature = Reference.simple(field.getName());
                if ( feature.isEmpty() || ! feature.get().name().equals("query")) continue;

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
        for (Map.Entry<String, OnnxModel> entry : onnxModels().entrySet()) {
            String modelName = entry.getKey();
            OnnxModel model = entry.getValue();
            Arguments args = new Arguments(new ReferenceNode(modelName));
            Map<String, TensorType> inputTypes = resolveOnnxInputTypes(model, context);

            TensorType defaultOutputType = model.getTensorType(model.getDefaultOutput(), inputTypes);
            context.setType(new Reference("onnxModel", args, null), defaultOutputType);

            for (Map.Entry<String, String> mapping : model.getOutputMap().entrySet()) {
                TensorType type = model.getTensorType(mapping.getKey(), inputTypes);
                context.setType(new Reference("onnxModel", args, mapping.getValue()), type);
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
                if (reference.get().name().equals("rankingExpression") && reference.get().simpleArgument().isPresent()) {
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

    private void addAttributeFeatureTypes(ImmutableSDField field, Map<Reference, TensorType> featureTypes) {
        Attribute attribute = field.getAttribute();
        field.getAttributes().forEach((k, a) -> {
            String name = k;
            if (attribute == a)                              // this attribute should take the fields name
                name = field.getName();                      // switch to that - it is separate for imported fields
            featureTypes.put(FeatureNames.asAttributeFeature(name),
                            a.tensorType().orElse(TensorType.empty));
        });
    }

    /**
     * A rank setting. The identity of a rank setting is its field name and type (not value).
     * A rank setting is immutable.
     */
    public static class RankSetting implements Serializable {

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

            public String toString() {
                return "type: " + name;
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
            if (!(object instanceof RankSetting)) {
                return false;
            }
            RankSetting other = (RankSetting)object;
            return
                    fieldName.equals(other.fieldName) &&
                    type.equals(other.type);
        }

        @Override
        public String toString() {
            return type + " setting " + fieldName + ": " + value;
        }

    }

    /** A rank property. Rank properties are Value Objects */
    public static class RankProperty implements Serializable {

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
            if (! (object instanceof RankProperty)) return false;
            RankProperty other=(RankProperty)object;
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
            return "function " + function;
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
        private DiversitySettings diversity = null;
        private double evaluationPoint = 0.20;
        private double prePostFilterTippingPoint = 1.0;

        public void setDiversity(DiversitySettings value) {
            value.checkValid();
            diversity = value;
        }

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
        public DiversitySettings      getDiversity() { return diversity; }
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

}
