// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.language.process.Embedder;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;

import com.yahoo.search.query.Model;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.query.Properties;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.Select;
import com.yahoo.search.query.Trace;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.ConversionContext;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profiling.Profiling;
import com.yahoo.search.query.profiling.ProfilingParams;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.ranking.Matching;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.tensor.Tensor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps between the query model and text properties.
 * This can be done simpler by using reflection but the performance penalty was not worth it,
 * especially since we should be conservative in adding things to the query model.
 *
 * @author bratseth
 */
public class QueryProperties extends Properties {

    interface GetProperty {
        Object get(Query query);
    }
    interface SetProperty {
        void set(Query query, Object value);
    }

    private Query query;
    private final CompiledQueryProfileRegistry profileRegistry;
    private final Map<String, Embedder> embedders;

    private static final Set<String> reservedPrefix = Set.of(Model.MODEL, Presentation.PRESENTATION, Select.SELECT, Ranking.RANKING, Trace.TRACE);


    private record GetterSetter(GetProperty getter, SetProperty setter) {
        static GetterSetter of(GetProperty getter, SetProperty setter) {
            return new GetterSetter(getter, setter);
        }
    }

    private static void addDualCasedRM(Map<CompoundName, GetterSetter> map, String last, GetterSetter accessor) {
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCHING, last), accessor);
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCHING, last.toLowerCase()), accessor);
    }

    private static final Map<CompoundName, GetterSetter> properyAccessors = createPropertySetterMap();
    private static Map<CompoundName, GetterSetter> createPropertySetterMap() {
        Map<CompoundName, GetterSetter> map = new HashMap<>();
        map.put(CompoundName.fromComponents(Model.MODEL, Model.QUERY_STRING), GetterSetter.of(query -> query.getModel().getQueryString(), (query, value) -> query.getModel().setQueryString(asString(value, ""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.TYPE), GetterSetter.of(query -> query.getModel().getType(), (query, value) -> query.getModel().setType(asString(value, "ANY"))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.FILTER), GetterSetter.of(query -> query.getModel().getFilter(), (query, value) -> query.getModel().setFilter(asString(value, ""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.DEFAULT_INDEX), GetterSetter.of(query -> query.getModel().getDefaultIndex(), (query, value) -> query.getModel().setDefaultIndex(asString(value, ""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LANGUAGE), GetterSetter.of(query -> query.getModel().getLanguage(), (query, value) -> query.getModel().setLanguage(asString(value, ""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LOCALE), GetterSetter.of(query -> query.getModel().getLocale(), (query, value) -> query.getModel().setLocale(asString(value, ""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.ENCODING), GetterSetter.of(query -> query.getModel().getEncoding(), (query, value) -> query.getModel().setEncoding(asString(value,""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SOURCES), GetterSetter.of(query -> query.getModel().getSources(), (query, value) -> query.getModel().setSources(asString(value,""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SEARCH_PATH), GetterSetter.of(query -> query.getModel().getSearchPath(), (query, value) -> query.getModel().setSearchPath(asString(value,""))));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.RESTRICT), GetterSetter.of(query -> query.getModel().getRestrict(), (query, value) -> query.getModel().setRestrict(asString(value,""))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LOCATION), GetterSetter.of(query -> query.getRanking().getLocation(), (query, value) -> query.getRanking().setLocation(asString(value,""))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.PROFILE), GetterSetter.of(query -> query.getRanking().getProfile(), (query, value) -> query.getRanking().setProfile(asString(value,""))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SORTING), GetterSetter.of(query -> query.getRanking().getSorting(), (query, value) -> query.getRanking().setSorting(asString(value,""))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.FRESHNESS), GetterSetter.of(query -> query.getRanking().getFreshness(), (query, value) -> query.getRanking().setFreshness(asString(value, ""))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.QUERYCACHE), GetterSetter.of(query -> query.getRanking().getQueryCache(), (query, value) -> query.getRanking().setQueryCache(asBoolean(value, false))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RERANKCOUNT), GetterSetter.of(query -> query.getRanking().getRerankCount(), (query, value) -> query.getRanking().setRerankCount(asInteger(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.KEEPRANKCOUNT), GetterSetter.of(query -> query.getRanking().getKeepRankCount(), (query, value) -> query.getRanking().setKeepRankCount(asInteger(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RANKSCOREDROPLIMIT), GetterSetter.of(query -> query.getRanking().getRankScoreDropLimit(), (query, value) -> query.getRanking().setRankScoreDropLimit(asDouble(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LIST_FEATURES), GetterSetter.of(query -> query.getRanking().getListFeatures(), (query, value) -> query.getRanking().setListFeatures(asBoolean(value,false))));

        addDualCasedRM(map, Matching.TERMWISELIMIT, GetterSetter.of(query -> query.getRanking().getMatching().getTermwiseLimit(), (query, value) -> query.getRanking().getMatching().setTermwiselimit(asDouble(value, 1.0))));
        addDualCasedRM(map, Matching.NUMTHREADSPERSEARCH, GetterSetter.of(query -> query.getRanking().getMatching().getNumThreadsPerSearch(), (query, value) -> query.getRanking().getMatching().setNumThreadsPerSearch(asInteger(value, 1))));
        addDualCasedRM(map, Matching.NUMSEARCHPARTITIIONS, GetterSetter.of(query -> query.getRanking().getMatching().getNumSearchPartitions(), (query, value) -> query.getRanking().getMatching().setNumSearchPartitions(asInteger(value, 1))));
        addDualCasedRM(map, Matching.MINHITSPERTHREAD, GetterSetter.of(query -> query.getRanking().getMatching().getMinHitsPerThread(), (query, value) -> query.getRanking().getMatching().setMinHitsPerThread(asInteger(value, 0))));
        addDualCasedRM(map, Matching.POST_FILTER_THRESHOLD, GetterSetter.of(query -> query.getRanking().getMatching().getPostFilterThreshold(), (query, value) -> query.getRanking().getMatching().setPostFilterThreshold(asDouble(value, 1.0))));
        addDualCasedRM(map, Matching.APPROXIMATE_THRESHOLD, GetterSetter.of(query -> query.getRanking().getMatching().getApproximateThreshold(), (query, value) -> query.getRanking().getMatching().setApproximateThreshold(asDouble(value, 0.05))));

        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ATTRIBUTE), GetterSetter.of(query -> query.getRanking().getMatchPhase().getAttribute(), (query, value) -> query.getRanking().getMatchPhase().setAttribute(asString(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ASCENDING), GetterSetter.of(query -> query.getRanking().getMatchPhase().getAscending(), (query, value) -> query.getRanking().getMatchPhase().setAscending(asBoolean(value, false))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_HITS), GetterSetter.of(query -> query.getRanking().getMatchPhase().getMaxHits(), (query, value) -> query.getRanking().getMatchPhase().setMaxHits(asLong(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_FILTER_COVERAGE), GetterSetter.of(query -> query.getRanking().getMatchPhase().getMaxFilterCoverage(), (query, value) -> query.getRanking().getMatchPhase().setMaxFilterCoverage(asDouble(value, 0.2))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.ATTRIBUTE),GetterSetter.of(query -> query.getRanking().getMatchPhase().getDiversity().getAttribute(),  (query, value) -> query.getRanking().getMatchPhase().getDiversity().setAttribute(asString(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.MINGROUPS), GetterSetter.of(query -> query.getRanking().getMatchPhase().getDiversity().getMinGroups(), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setMinGroups(asLong(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.FACTOR), GetterSetter.of(query -> query.getRanking().getMatchPhase().getDiversity().getCutoffFactor(), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setCutoffFactor(asDouble(value, 10.0))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.STRATEGY), GetterSetter.of(query -> query.getRanking().getMatchPhase().getDiversity().getCutoffStrategy(), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setCutoffStrategy(asString(value, "loose"))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.ENABLE), GetterSetter.of(query -> query.getRanking().getSoftTimeout().getEnable(), (query, value) -> query.getRanking().getSoftTimeout().setEnable(asBoolean(value, true))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.FACTOR), GetterSetter.of(query -> query.getRanking().getSoftTimeout().getFactor(), (query, value) -> query.getRanking().getSoftTimeout().setFactor(asDouble(value, null))));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.TAILCOST), GetterSetter.of(query -> query.getRanking().getSoftTimeout().getTailcost(), (query, value) -> query.getRanking().getSoftTimeout().setTailcost(asDouble(value, null))));
        map.put(CompoundName.fromComponents(Select.SELECT), GetterSetter.of(query -> query.getSelect().getGroupingExpressionString(), (query, value) -> query.getSelect().setGroupingExpressionString(asString(value, ""))));
        map.put(CompoundName.fromComponents(Select.SELECT, Select.WHERE), GetterSetter.of(query -> query.getSelect().getWhereString(), (query, value) -> query.getSelect().setWhereString(asString(value, ""))));
        map.put(CompoundName.fromComponents(Select.SELECT, Select.GROUPING), GetterSetter.of(query -> query.getSelect().getGroupingString(), (query, value) -> query.getSelect().setGroupingString(asString(value, ""))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.LEVEL), GetterSetter.of(query -> query.getTrace().getLevel(), (query, value) -> query.getTrace().setLevel(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.EXPLAIN_LEVEL), GetterSetter.of(query -> query.getTrace().getExplainLevel(), (query, value) -> query.getTrace().setExplainLevel(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.PROFILE_DEPTH), GetterSetter.of(null, (query, value) -> query.getTrace().setProfileDepth(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.TIMESTAMPS), GetterSetter.of(query -> query.getTrace().getTimestamps(), (query, value) -> query.getTrace().setTimestamps(asBoolean(value, false))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.QUERY), GetterSetter.of(query -> query.getTrace().getQuery(), (query, value) -> query.getTrace().setQuery(asBoolean(value, true))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.PROFILING, Profiling.MATCHING, ProfilingParams.DEPTH), GetterSetter.of(query -> query.getTrace().getProfiling().getMatching().getDepth(), (query, value) -> query.getTrace().getProfiling().getMatching().setDepth(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.PROFILING, Profiling.FIRST_PHASE_RANKING, ProfilingParams.DEPTH), GetterSetter.of(query -> query.getTrace().getProfiling().getFirstPhaseRanking().getDepth(), (query, value) -> query.getTrace().getProfiling().getFirstPhaseRanking().setDepth(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.PROFILING, Profiling.SECOND_PHASE_RANKING, ProfilingParams.DEPTH), GetterSetter.of(query -> query.getTrace().getProfiling().getSecondPhaseRanking().getDepth(), (query, value) -> query.getTrace().getProfiling().getSecondPhaseRanking().setDepth(asInteger(value, 0))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.BOLDING), GetterSetter.of(query -> query.getPresentation().getBolding(), (query, value) -> query.getPresentation().setBolding(asBoolean(value, true))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY), GetterSetter.of(query -> query.getPresentation().getSummary(), (query, value) -> query.getPresentation().setSummary(asString(value, ""))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT), GetterSetter.of(query -> query.getPresentation().getFormat(), (query, value) -> query.getPresentation().setFormat(asString(value, ""))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.TIMING), GetterSetter.of(query -> query.getPresentation().getTiming(), (query, value) -> query.getPresentation().setTiming(asBoolean(value, true))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY_FIELDS), GetterSetter.of(query -> query.getPresentation().getSummaryFields(), (query, value) -> query.getPresentation().setSummaryFields(asString(value, ""))));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT, Presentation.TENSORS), GetterSetter.of(query -> query.getPresentation().getTensorShortForm(), (query, value) -> query.getPresentation().setTensorFormat(asString(value, "short")))); // TODO: Switch default to short-value on Vespa 9);
        map.put(Query.HITS, GetterSetter.of(Query::getHits, (query, value) -> query.setHits(asInteger(value,10))));
        map.put(Query.OFFSET, GetterSetter.of(Query::getOffset, (query, value) -> query.setOffset(asInteger(value,0))));
        map.put(Query.TIMEOUT, GetterSetter.of(Query::getTimeout, (query, value) -> query.setTimeout(value.toString())));
        map.put(Query.NO_CACHE, GetterSetter.of(Query::getNoCache, (query, value) -> query.setNoCache(asBoolean(value,false))));
        map.put(Query.GROUPING_SESSION_CACHE, GetterSetter.of(Query::getGroupingSessionCache, (query, value) -> query.setGroupingSessionCache(asBoolean(value, true))));

        map.put(CompoundName.fromComponents(Model.MODEL), GetterSetter.of(Query::getModel, null));
        map.put(CompoundName.fromComponents(Ranking.RANKING), GetterSetter.of(Query::getRanking, null));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION), GetterSetter.of(Query::getPresentation, null));
        return map;
    }

    public QueryProperties(Query query, CompiledQueryProfileRegistry profileRegistry, Map<String, Embedder> embedders) {
        this.query = query;
        this.profileRegistry = profileRegistry;
        this.embedders = embedders;
    }

    public void setParentQuery(Query query) {
        this.query = query;
        super.setParentQuery(query);
    }

    @Override
    public Object get(CompoundName key,
                      Map<String, String> context,
                      com.yahoo.processing.request.Properties substitution) {
        GetterSetter propertyAccessor = properyAccessors.get(key);
        if (propertyAccessor != null && propertyAccessor.getter != null) return propertyAccessor.getter.get(query);

        if (key.first().equals(Ranking.RANKING)) {
            Ranking ranking = query.getRanking();
            if (key.size() > 2) {
                // pass the portion after "ranking.features/properties" down
                if (key.get(1).equals(Ranking.FEATURES)) return ranking.getFeatures().getObject(key.rest().rest().toString());
                if (key.get(1).equals(Ranking.PROPERTIES)) return ranking.getProperties().get(key.rest().rest().toString());
            }
        }

        return super.get(key, context, substitution);
    }

    private void setInternal(CompoundName key, Object value, Map<String,String> context) {
        GetterSetter propertyAccessor = properyAccessors.get(key);
        if (propertyAccessor != null && propertyAccessor.setter != null) {
            propertyAccessor.setter.set(query, value);
            return;
        }

        //TODO Why is there error handling in set path and not in get path ?
        if (key.first().equals(Ranking.RANKING)) {
            if (key.size() > 2) {
                String restKey = key.rest().rest().toString();
                chained().requireSettable(key, value, context);
                if (key.get(1).equals(Ranking.FEATURES)) {
                    setRankFeature(query, restKey, toSpecifiedType(restKey, value,
                            profileRegistry.getTypeRegistry().getComponent("features"),
                            context));
                    return;
                } else if (key.get(1).equals(Ranking.PROPERTIES)) {
                    Ranking ranking = query.getRanking();
                    ranking.getProperties().put(restKey, toSpecifiedType(restKey, value,
                            profileRegistry.getTypeRegistry().getComponent("properties"),
                            context));
                    return;
                }
            }
        }
        if (reservedPrefix.contains(key.first())) {
            throwIllegalParameter(key.rest().toString(), key.first());
        } else {
            super.set(key, value, context);
        }
    }

    @Override
    public void set(CompoundName key, Object value, Map<String,String> context) {
        // Note: The defaults here are never used
        try {
            setInternal(key, value, context);
        }
        catch (Exception e) { // Make sure error messages are informative. This should be moved out of this properties implementation
            if (e.getMessage() != null && e.getMessage().startsWith("Could not set"))
                throw e;
            else
                throw new IllegalInputException("Could not set '" + key + "' to '" + value + "'", e);
        }
    }

    @Override
    public Map<String, Object> listProperties(CompoundName prefix, Map<String, String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        Map<String, Object> properties = super.listProperties(prefix, context, substitution);
        for (CompoundName queryProperty : Query.nativeProperties) {
            if (queryProperty.hasPrefix(prefix)) {
                Object value = this.get(queryProperty, context, substitution);
                if (value != null)
                    properties.put(queryProperty.toString(), value);
            }
        }
        return properties;
    }

    private void setRankFeature(Query query, String key, Object value) {
        if (value instanceof Tensor) {
            query.getRanking().getFeatures().put(key, (Tensor) value);
        }
        else if (value instanceof Double) {
            query.getRanking().getFeatures().put(key, (Double) value);
        }
        else {
            String valueString = asString(value, "");
            try {
                query.getRanking().getFeatures().put(key, Double.parseDouble(valueString));
            }
            catch (IllegalArgumentException e) {
                query.getRanking().getFeatures().put(key, valueString);
            }
        }
    }

    private Object toSpecifiedType(String key, Object value, QueryProfileType type, Map<String,String> context) {
        if ( ! ( value instanceof String)) return value; // already typed
        if (type == null) return value; // no type info -> keep as string
        FieldDescription field = type.getField(key);
        if (field == null) return value; // ditto
        return field.getType().convertFrom(value, new ConversionContext(key, profileRegistry, embedders, context));
    }

    private void throwIllegalParameter(String key, String namespace) {
        throw new IllegalInputException("'" + key + "' is not a valid property in '" + namespace +
                                        "'. See the query api for valid keys starting by '" + namespace + "'.");
    }

    @Override
    public final Query getParentQuery() {
        return query;
    }

}
