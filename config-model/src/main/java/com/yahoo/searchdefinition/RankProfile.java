// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.expressiontransforms.RankProfileTransformContext;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.searchlib.rankingexpression.ExpressionFunction;
import com.yahoo.searchlib.rankingexpression.FeatureList;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.TensorType;
import com.yahoo.tensor.evaluation.TypeContext;

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
import java.util.Set;

/**
 * Represents a rank profile - a named set of ranking settings
 *
 * @author bratseth
 */
public class RankProfile implements Serializable, Cloneable {

    /** The search definition-unique name of this rank profile */
    private final String name;

    /** The search definition owning this profile, or null if none */
    private Search search = null;

    /** The name of the rank profile inherited by this */
    private String inheritedName = null;

    /** The match settings of this profile */
    protected MatchPhaseSettings matchPhaseSettings = null;

    /** The rank settings of this profile */
    protected Set<RankSetting> rankSettings = new java.util.LinkedHashSet<>();

    /** The ranking expression to be used for first phase */
    private RankingExpression firstPhaseRanking = null;

    /** The ranking expression to be used for second phase */
    private RankingExpression secondPhaseRanking = null;

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

    private Set<ReferenceNode> rankFeatures;

    /** The properties of this - a multimap */
    private Map<String, List<RankProperty>> rankProperties = new LinkedHashMap<>();

    private Boolean ignoreDefaultRankFeatures=null;

    private String secondPhaseRankingString=null;

    private String firstPhaseRankingString=null;

    private Map<String, Macro> macros= new LinkedHashMap<>();

    private Set<String> filterFields = new HashSet<>();

    private final RankProfileRegistry rankProfileRegistry;

    /** Constants in ranking expressions */
    private Map<String, Value> constants = new HashMap<>();

    private final TypeSettings attributeTypes = new TypeSettings();

    private final TypeSettings queryFeatureTypes = new TypeSettings();

    /**
     * Creates a new rank profile
     *
     * @param name   the name of the new profile
     * @param search the search definition owning this profile
     * @param rankProfileRegistry The {@link com.yahoo.searchdefinition.RankProfileRegistry} to use for storing
     *                            and looking up rank profiles.
     */
    public RankProfile(String name, Search search, RankProfileRegistry rankProfileRegistry) {
        this.name = name;
        this.search = search;
        this.rankProfileRegistry = rankProfileRegistry;
    }

    public String getName() { return name; }

    /**
     * Returns the search definition owning this, or null if none
     *
     * @return The search definition.
     */
    public Search getSearch() {
        return search;
    }

    /**
     * Sets the name of the rank profile this inherits. Both rank profiles must be present in the same search
     * definition
     *
     * @param inheritedName The name of the profile that this inherits from.
     */
    public void setInherited(String inheritedName) {
        this.inheritedName = inheritedName;
    }

    /**
     * Returns the name of the profile this one inherits, or null if none is inherited
     *
     * @return The inherited name.
     */
    public String getInheritedName() {
        return inheritedName;
    }

    /**
     * Returns the inherited rank profile, or null if there is none
     *
     * @return The inherited profile.
     */
    public RankProfile getInherited() {
        if (getSearch()==null) return getInheritedFromRegistry(inheritedName);
        RankProfile inheritedInThisSearch = rankProfileRegistry.getRankProfile(search, inheritedName);
        if (inheritedInThisSearch!=null) return inheritedInThisSearch;
        return getInheritedFromRegistry(inheritedName);
    }

    private RankProfile getInheritedFromRegistry(String inheritedName) {
        for (RankProfile r : rankProfileRegistry.allRankProfiles()) {
            if (r.getName().equals(inheritedName)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Returns whether this profile inherits (directly or indirectly) the given profile
     *
     * @param name The profile name to compare this to.
     * @return Whether or not this inherits from the named profile.
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

    /**
     * change match settings
     * @param settings The new match settings
     **/
    public void setMatchPhaseSettings(MatchPhaseSettings settings) {
        settings.checkValid();
        this.matchPhaseSettings = settings;
    }

    public MatchPhaseSettings getMatchPhaseSettings() {
        MatchPhaseSettings settings = this.matchPhaseSettings;
        if (settings != null ) return settings;
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
     * @param field The field whose settings to return.
     * @param type  The type that the field is required to be.
     * @return The rank setting found, or null.
     */
    public RankSetting getDeclaredRankSetting(String field, RankSetting.Type type) {
        for (Iterator<RankSetting> i = declaredRankSettingIterator(); i.hasNext();) {
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
     * @param field The field whose settings to return.
     * @param type  The type that the field is required to be.
     * @return The rank setting found, or null.
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
     * @return An iterator for the declared rank setting.
     */
    public Iterator<RankSetting> declaredRankSettingIterator() {
        return Collections.unmodifiableSet(rankSettings).iterator();
    }

    /**
     * Returns all settings in this profile or any profile it inherits
     *
     * @return An iterator for all rank settings of this.
     */
    public Iterator<RankSetting> rankSettingIterator() {
        return rankSettings().iterator();
    }

    /**
     * Returns a snapshot of the rank settings of this and everything it inherits
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
        if (firstPhaseRanking!=null) return firstPhaseRanking;
        if (getInherited()!=null) return getInherited().getFirstPhaseRanking();
        return null;
    }

    public void setFirstPhaseRanking(RankingExpression rankingExpression) {
        this.firstPhaseRanking=rankingExpression;
    }

    /**
     * Returns the ranking expression to use by this. This expression must not be edited.
     * Returns null if no expression is set.
     */
    public RankingExpression getSecondPhaseRanking() {
        if (secondPhaseRanking!=null) return secondPhaseRanking;
        if (getInherited()!=null) return getInherited().getSecondPhaseRanking();
        return null;
    }

    public void setSecondPhaseRanking(RankingExpression rankingExpression) {
        this.secondPhaseRanking=rankingExpression;
    }

    /**
     * Called by parser to store the expression string, for delayed evaluation
     * @param exp ranking expression for second phase
     */
    public void setSecondPhaseRankingString(String exp) {
        this.secondPhaseRankingString = exp;
    }

    /**
     * Called by parser to store the expression string, for delayed evaluation
     * @param exp ranking expression for first phase
     */
    public void setFirstPhaseRankingString(String exp) {
        this.firstPhaseRankingString = exp;
    }

    /** Returns a read-only view of the summary features to use in this profile. This is never null */
    public Set<ReferenceNode> getSummaryFeatures() {
        if (summaryFeatures!=null) return Collections.unmodifiableSet(summaryFeatures);
        if (getInherited()!=null) return getInherited().getSummaryFeatures();
        return Collections.emptySet();
    }

    public void addSummaryFeature(ReferenceNode feature) {
        if (summaryFeatures==null)
            summaryFeatures=new LinkedHashSet<>();
        summaryFeatures.add(feature);
    }

    /**
     * Adds the content of the given feature list to the internal list of summary features.
     *
     * @param features The features to add.
     */
    public void addSummaryFeatures(FeatureList features) {
        for (ReferenceNode feature : features) {
            addSummaryFeature(feature);
        }
    }

    /** Returns a read-only view of the rank features to use in this profile. This is never null */
    public Set<ReferenceNode> getRankFeatures() {
        if (rankFeatures != null) return Collections.unmodifiableSet(rankFeatures);
        if (getInherited() != null) return getInherited().getRankFeatures();
        return Collections.emptySet();
    }

    public void addRankFeature(ReferenceNode feature) {
        if (rankFeatures==null)
            rankFeatures=new LinkedHashSet<>();
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

    public void addRankProperty(RankProperty rankProperty) {
        // Just the usual multimap semantics here
        List<RankProperty> properties = rankProperties.get(rankProperty.getName());
        if (properties == null) {
            properties = new ArrayList<>(1);
            rankProperties.put(rankProperty.getName(), properties);
        }
        properties.add(rankProperty);
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

    public double getTermwiseLimit() {
        return ((termwiseLimit == null) && (getInherited() != null))
                ?  getInherited().getTermwiseLimit()
                : (termwiseLimit != null) ? termwiseLimit : 1.0;
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
        if (ignoreDefaultRankFeatures!=null) return ignoreDefaultRankFeatures;
        return (getInherited()!=null) && getInherited().getIgnoreDefaultRankFeatures();
    }

    /**
     * Returns the string form of the second phase ranking expression.
     *
     * @return string form of second phase ranking expression
     */
    public String getSecondPhaseRankingString() {
        if (secondPhaseRankingString != null) return secondPhaseRankingString;
        if (getInherited() != null) return getInherited().getSecondPhaseRankingString();
        return null;
    }

    /**
     * Returns the string form of the first phase ranking expression.
     *
     * @return string form of first phase ranking expression
     */
    public String getFirstPhaseRankingString() {
        if (firstPhaseRankingString != null) return firstPhaseRankingString;
        if (getInherited() != null) return getInherited().getFirstPhaseRankingString();
        return null;
    }

    public void addMacro(String name, boolean inline) {
        macros.put(name, new Macro(name, inline));
    }

    /** Returns an unmodifiable view of the macros in this */
    public Map<String, Macro> getMacros() {
        if (macros.size() == 0 && getInherited()==null) return Collections.emptyMap();
        if (macros.size() == 0) return getInherited().getMacros();
        if (getInherited() == null) return Collections.unmodifiableMap(macros);

        // Neither is null
        Map<String, Macro> allMacros = new LinkedHashMap<>(getInherited().getMacros());
        allMacros.putAll(macros);
        return Collections.unmodifiableMap(allMacros);

    }

    public int getKeepRankCount() {
        if (keepRankCount>=0) return keepRankCount;
        if (getInherited()!=null) return getInherited().getKeepRankCount();
        return -1;
    }

    public void setKeepRankCount(int rerankArraySize) {
        this.keepRankCount = rerankArraySize;
    }

    public double getRankScoreDropLimit() {
        if (rankScoreDropLimit>-Double.MAX_VALUE) return rankScoreDropLimit;
        if (getInherited()!=null) return getInherited().getRankScoreDropLimit();
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

    /**
     * Will take the parser-set textual ranking expressions and turn into objects
     */
    public void parseExpressions() {
        try {
            parseRankingExpressions();
            parseMacros();
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Passes the contents of macros on to parser. Then put all the implied rank properties
     * from those macros into the profile's props map.
     */
    private void parseMacros() throws ParseException {
        for (Map.Entry<String, Macro> e : getMacros().entrySet()) {
            String macroName = e.getKey();
            Macro macro = e.getValue();
            RankingExpression expr = parseRankingExpression(macroName, macro.getTextualExpression());
            macro.setRankingExpression(expr);
            macro.setTextualExpression(expr.getRoot().toString());
        }
    }

    /**
     * Passes ranking expressions on to parser
     * @throws ParseException if either of the ranking expressions could not be parsed
     */
    private void parseRankingExpressions() throws ParseException {
        if (getFirstPhaseRankingString() != null)
            setFirstPhaseRanking(parseRankingExpression("firstphase", getFirstPhaseRankingString()));
        if (getSecondPhaseRankingString() != null)
            setSecondPhaseRanking(parseRankingExpression("secondphase", getSecondPhaseRankingString()));
    }

    private RankingExpression parseRankingExpression(String expressionName, String exp) throws ParseException {
        if (exp.trim().length() == 0)
            throw new ParseException("Encountered an empty ranking expression in " + getName()+ ", " + expressionName + ".");

        try (Reader rankingExpressionReader = openRankingExpressionReader(expressionName, exp.trim())) {
            return new RankingExpression(expressionName, rankingExpressionReader);
        }
        catch (com.yahoo.searchlib.rankingexpression.parser.ParseException e) {
            ParseException exception = new ParseException("Could not parse ranking expression '" + exp.trim() +
                                                          "' in " + getName()+ ", " + expressionName + ".");
            throw (ParseException)exception.initCause(e);
        }
        catch (IOException e) {
            throw new RuntimeException("IOException parsing ranking expression '" + expressionName + "'");
        }
    }

    private Reader openRankingExpressionReader(String expName, String expression) {
        if ( ! expression.startsWith("file:")) return new StringReader(expression);

        String fileName = expression.substring("file:".length()).trim();
        if ( ! fileName.endsWith(ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX))
            fileName = fileName + ApplicationPackage.RANKEXPRESSION_NAME_SUFFIX;

        File file = new File(fileName);
        if ( ! (file.isAbsolute()) && file.getPath().contains("/")) // See ticket 4102122
            throw new IllegalArgumentException("In " + getName() +", " + expName + ", ranking references file '" + file +
                                               "' in subdirectory, which is not supported.");

        return search.getRankingExpression(fileName);
    }

    /** Shallow clones this */
    @Override
    public RankProfile clone() {
        try {
            // Note: This treats RankingExpression in Macros as immutables even though they are not
            RankProfile clone = (RankProfile)super.clone();
            clone.rankSettings = new LinkedHashSet<>(this.rankSettings);
            clone.matchPhaseSettings = this.matchPhaseSettings; // hmm?
            clone.summaryFeatures = summaryFeatures != null ? new LinkedHashSet<>(this.summaryFeatures) : null;
            clone.rankFeatures = rankFeatures != null ? new LinkedHashSet<>(this.rankFeatures) : null;
            clone.rankProperties = new LinkedHashMap<>(this.rankProperties);
            clone.macros = new LinkedHashMap<>(this.macros);
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
    public RankProfile compile(QueryProfileRegistry queryProfiles) {
        try {
            RankProfile compiled = this.clone();
            compiled.compileThis(queryProfiles);
            return compiled;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rank profile '" + getName() + "' is invalid", e);
        }
    }

    private void compileThis(QueryProfileRegistry queryProfiles) {
        parseExpressions();

        checkNameCollisions(getMacros(), getConstants());

        Map<String, Macro> compiledMacros = new LinkedHashMap<>();
        for (Map.Entry<String, Macro> macroEntry : getMacros().entrySet()) {
            Macro compiledMacro = macroEntry.getValue().clone();
            compiledMacro.setRankingExpression(compile(macroEntry.getValue().getRankingExpression(),
                                                       queryProfiles,
                                                       getConstants(), Collections.<String, Macro>emptyMap()));
            compiledMacros.put(macroEntry.getKey(), compiledMacro);
        }
        macros = compiledMacros;
        Map<String, Macro> inlineMacros = keepInline(compiledMacros);
        firstPhaseRanking = compile(this.getFirstPhaseRanking(), queryProfiles, getConstants(), inlineMacros);
        secondPhaseRanking = compile(this.getSecondPhaseRanking(), queryProfiles, getConstants(), inlineMacros);
    }

    private void checkNameCollisions(Map<String, Macro> macros, Map<String, Value> constants) {
        for (Map.Entry<String, Macro> macroEntry : macros.entrySet()) {
            if (constants.get(macroEntry.getKey()) != null)
                throw new IllegalArgumentException("Cannot have both a constant and macro named '" +
                                                   macroEntry.getKey() + "'");
        }
    }

    private Map<String, Macro> keepInline(Map<String, Macro> macros) {
        Map<String, Macro> inlineMacros = new HashMap<>();
        for (Map.Entry<String, Macro> entry : macros.entrySet())
            if (entry.getValue().getInline())
                inlineMacros.put(entry.getKey(), entry.getValue());
        return inlineMacros;
    }

    private RankingExpression compile(RankingExpression expression,
                                      QueryProfileRegistry queryProfiles,
                                      Map<String, Value> constants,
                                      Map<String, Macro> inlineMacros) {
        if (expression == null) return null;
        Map<String, String> rankPropertiesOutput = new HashMap<>();

        RankProfileTransformContext context = new RankProfileTransformContext(this,
                                                                              queryProfiles,
                                                                              constants,
                                                                              inlineMacros,
                                                                              rankPropertiesOutput);
        expression = rankProfileRegistry.expressionTransforms().transform(expression, context);
        for (Map.Entry<String, String> rankProperty : rankPropertiesOutput.entrySet()) {
            addRankProperty(rankProperty.getKey(), rankProperty.getValue());
        }
        return expression;
    }

    /**
     * Creates a context containing the type information of all constants, attributes and query profiles
     * referable from this rank profile.
     */
    public TypeContext typeContext(QueryProfileRegistry queryProfiles) {
        TypeMapContext context = new TypeMapContext();

        // Add constants
        getSearch().getRankingConstants().forEach((k, v) -> context.setType(FeatureNames.asConstantFeature(k), v.getTensorType()));

        // Add attributes
        for (SDField field : getSearch().allConcreteFields()) {
            field.getAttributes().forEach((k, a) -> context.setType(FeatureNames.asAttributeFeature(k), a.tensorType().orElse(TensorType.empty)));
        }

        // Add query features from rank profile types reached from the "default" profile
        for (QueryProfileType queryProfileType : queryProfiles.getTypeRegistry().allComponents()) {
            for (FieldDescription field : queryProfileType.declaredFields().values()) {
                TensorType type = field.getType().asTensorType();
                String feature = FeatureNames.asQueryFeature(field.getName());
                TensorType existingType = context.getType(feature);
                if (existingType != null)
                    type = existingType.dimensionwiseGeneralizationWith(type).orElseThrow( () ->
                        new IllegalArgumentException(queryProfileType + " contains query feature " + feature +
                                                     " with type " + field.getType().asTensorType() +
                                                     ", but this is already defined " +
                                                     "in another query profile with type " + context.getType(feature)));
                context.setType(feature, type);
            }
        }

        return context;
    }

    /**
     * A rank setting. The identity of a rank setting is its field name and type (not value).
     * A rank setting is immutable.
     */
    public static class RankSetting implements Serializable {

        private String fieldName;

        private Type type;

        /** The rank value */
        private Object value;

        public enum Type {

            RANKTYPE("rank-type"),
            LITERALBOOST("literal-boost"),
            WEIGHT("weight"),
            PREFERBITVECTOR("preferbitvector",true);

            private String name;

            /** True if this setting really pertains to an index, not a field within an index */
            private boolean isIndexLevel;

            private Type(String name) {
                this(name,false);
            }

            private Type(String name,boolean isIndexLevel) {
                this.name = name;
                this.isIndexLevel=isIndexLevel;
            }

            /** True if this setting really pertains to an index, not a field within an index */
            public boolean isIndexLevel() { return isIndexLevel; }

            /** @return The name of this type */
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

        /** @return The value as an int, or a negative value if it is not an integer */
        public int getIntValue() {
            if (value instanceof Integer) {
                return ((Integer)value);
            }
            else {
                return -1;
            }
        }

        public int hashCode() {
            return fieldName.hashCode() + 17 * type.hashCode();
        }

        public boolean equals(Object object) {
            if (!(object instanceof RankSetting)) {
                return false;
            }
            RankSetting other = (RankSetting)object;
            return
                    fieldName.equals(other.fieldName) &&
                    type.equals(other.type);
        }

        public String toString() {
            return type + " setting " + fieldName + ": " + value;
        }

    }

    /** A rank property. Rank properties are Value Objects */
    public static class RankProperty implements Serializable {

        private String name;
        private String value;

        public RankProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() { return name; }

        public String getValue() { return value; }

        @Override
        public int hashCode() {
            return name.hashCode() + 17*value.hashCode();
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

    /**
     * Represents a declared macro in the profile. It is, after parsing, transformed into ExpressionMacro
     *
     * @author vegardh
     */
    public static class Macro implements Serializable, Cloneable {

        private final String name;
        private String textualExpression=null;
        private RankingExpression expression=null;
        private List<String> formalParams = new ArrayList<>();

        /** True if this should be inlined into calling expressions. Useful for very cheap macros. */
        private final boolean inline;

        public Macro(String name, boolean inline) {
            this.name = name;
            this.inline = inline;
        }

        public void addParam(String name) {
            formalParams.add(name);
        }

        public List<String> getFormalParams() {
            return formalParams;
        }

        public String getTextualExpression() {
            return textualExpression;
        }

        public void setTextualExpression(String textualExpression) {
            this.textualExpression = textualExpression;
        }

        public void setRankingExpression(RankingExpression expr) {
            this.expression=expr;
        }

        public RankingExpression getRankingExpression() {
            return expression;
        }

        public String getName() {
            return name;
        }

        public boolean getInline() {
            return inline && formalParams.size() == 0; // only inline no-arg macros;
        }

        public ExpressionFunction toExpressionMacro() {
            return new ExpressionFunction(getName(), getFormalParams(), getRankingExpression());
        }

        @Override
        public Macro clone() {
            try {
                return (Macro)super.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException("Won't happen", e);
            }
        }

        @Override
        public String toString() {
            return "macro " + getName() + ": " + expression;
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
        public void setCutoffStrategy(String strategy) { cutoffStrategy = Diversity.CutoffStrategy.valueOf(strategy); }
        public String getAttribute() { return attribute; }
        public int getMinGroups() { return minGroups; }
        public double getCutoffFactor() { return cutoffFactor; }
        public Diversity.CutoffStrategy getCutoffStrategy() { return cutoffStrategy; }
        public void checkValid() {
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

        public void addType(String name, String type) {
            types.put(name, type);
        }

        public Map<String, String> getTypes() {
            return Collections.unmodifiableMap(types);
        }
    }

}
