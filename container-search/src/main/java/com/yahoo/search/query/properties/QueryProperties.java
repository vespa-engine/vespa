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

    //TODO: Make a common getter/setter map
    private static final Map<CompoundName, GetProperty> getMap = createPropertyGetterMap();
    private static final Map<CompoundName, SetProperty> setMap = createPropertySetterMap();
    private static Map<CompoundName, SetProperty> createPropertySetterMap() {
        Map<CompoundName, SetProperty> map = new HashMap<>();
        map.put(CompoundName.fromComponents(Model.MODEL, Model.QUERY_STRING), (query, value) -> query.getModel().setQueryString(asString(value, "")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.TYPE), (query, value) -> query.getModel().setType(asString(value, "ANY")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.FILTER), (query, value) -> query.getModel().setFilter(asString(value, "")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.DEFAULT_INDEX), (query, value) -> query.getModel().setDefaultIndex(asString(value, "")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LANGUAGE), (query, value) -> query.getModel().setLanguage(asString(value, "")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LOCALE), (query, value) -> query.getModel().setLocale(asString(value, "")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.ENCODING), (query, value) -> query.getModel().setEncoding(asString(value,"")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SOURCES), (query, value) -> query.getModel().setSources(asString(value,"")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SEARCH_PATH), (query, value) -> query.getModel().setSearchPath(asString(value,"")));
        map.put(CompoundName.fromComponents(Model.MODEL, Model.RESTRICT), (query, value) -> query.getModel().setRestrict(asString(value,"")));

        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LOCATION), (query, value) -> query.getRanking().setLocation(asString(value,"")));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.PROFILE), (query, value) -> query.getRanking().setProfile(asString(value,"")));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SORTING), (query, value) -> query.getRanking().setSorting(asString(value,"")));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.FRESHNESS), (query, value) -> query.getRanking().setFreshness(asString(value, "")));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.QUERYCACHE), (query, value) -> query.getRanking().setQueryCache(asBoolean(value, false)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RERANKCOUNT), (query, value) -> query.getRanking().setRerankCount(asInteger(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.KEEPRANKCOUNT), (query, value) -> query.getRanking().setKeepRankCount(asInteger(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RANKSCOREDROPLIMIT), (query, value) -> query.getRanking().setRankScoreDropLimit(asDouble(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LIST_FEATURES), (query, value) -> query.getRanking().setListFeatures(asBoolean(value,false)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ATTRIBUTE), (query, value) -> query.getRanking().getMatchPhase().setAttribute(asString(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ASCENDING), (query, value) -> query.getRanking().getMatchPhase().setAscending(asBoolean(value, false)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_HITS), (query, value) -> query.getRanking().getMatchPhase().setMaxHits(asLong(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_FILTER_COVERAGE), (query, value) -> query.getRanking().getMatchPhase().setMaxFilterCoverage(asDouble(value, 0.2)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.ATTRIBUTE), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setAttribute(asString(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.MINGROUPS), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setMinGroups(asLong(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.FACTOR), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setCutoffFactor(asDouble(value, 10.0)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.STRATEGY), (query, value) -> query.getRanking().getMatchPhase().getDiversity().setCutoffStrategy(asString(value, "loose")));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.ENABLE), (query, value) -> query.getRanking().getSoftTimeout().setEnable(asBoolean(value, true)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.FACTOR), (query, value) -> query.getRanking().getSoftTimeout().setFactor(asDouble(value, null)));
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.TAILCOST), (query, value) -> query.getRanking().getSoftTimeout().setTailcost(asDouble(value, null)));
        map.put(CompoundName.fromComponents(Select.SELECT), (query, value) -> query.getSelect().setGroupingExpressionString(asString(value, "")));
        map.put(CompoundName.fromComponents(Select.SELECT, Select.WHERE), (query, value) -> query.getSelect().setWhereString(asString(value, "")));
        map.put(CompoundName.fromComponents(Select.SELECT, Select.GROUPING), (query, value) -> query.getSelect().setGroupingString(asString(value, "")));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.LEVEL), (query, value) -> query.getTrace().setLevel(asInteger(value, 0)));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.EXPLAIN_LEVEL), (query, value) -> query.getTrace().setExplainLevel(asInteger(value, 0)));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.PROFILE_DEPTH), (query, value) -> query.getTrace().setProfileDepth(asInteger(value, 0)));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.TIMESTAMPS), (query, value) -> query.getTrace().setTimestamps(asBoolean(value, false)));
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.QUERY), (query, value) -> query.getTrace().setQuery(asBoolean(value, true)));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.BOLDING), (query, value) -> query.getPresentation().setBolding(asBoolean(value, true)));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY), (query, value) -> query.getPresentation().setSummary(asString(value, "")));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT), (query, value) -> query.getPresentation().setFormat(asString(value, "")));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.TIMING), (query, value) -> query.getPresentation().setTiming(asBoolean(value, true)));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY_FIELDS), (query, value) -> query.getPresentation().setSummaryFields(asString(value, "")));
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT, Presentation.TENSORS), (query, value) -> query.getPresentation().setTensorFormat(asString(value, "short"))); // TODO: Switch default to short-value on Vespa 9);

        map.put(Query.HITS, (query, value) -> query.setHits(asInteger(value,10)));
        map.put(Query.OFFSET, (query, value) -> query.setOffset(asInteger(value,0)));
        map.put(Query.TIMEOUT, (query, value) -> query.setTimeout(value.toString()));
        map.put(Query.NO_CACHE, (query, value) -> query.setNoCache(asBoolean(value,false)));
        map.put(Query.GROUPING_SESSION_CACHE, (query, value) -> query.setGroupingSessionCache(asBoolean(value, true)));
        return map;
    }
    private static Map<CompoundName, GetProperty> createPropertyGetterMap() {
        Map<CompoundName, GetProperty> map = new HashMap<>();
        map.put(CompoundName.fromComponents(Model.MODEL, Model.QUERY_STRING), query -> query.getModel().getQueryString());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.TYPE), query -> query.getModel().getType());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.FILTER), query -> query.getModel().getFilter());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.DEFAULT_INDEX), query -> query.getModel().getDefaultIndex());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LANGUAGE), query -> query.getModel().getLanguage());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.LOCALE), query -> query.getModel().getLocale());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.ENCODING), query -> query.getModel().getEncoding());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SOURCES), query -> query.getModel().getSources());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.SEARCH_PATH), query -> query.getModel().getSearchPath());
        map.put(CompoundName.fromComponents(Model.MODEL, Model.RESTRICT), query -> query.getModel().getRestrict());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LOCATION), query -> query.getRanking().getLocation());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.PROFILE), query -> query.getRanking().getProfile());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SORTING), query -> query.getRanking().getSorting());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.FRESHNESS), query -> query.getRanking().getFreshness());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.QUERYCACHE), query -> query.getRanking().getQueryCache());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RERANKCOUNT), query -> query.getRanking().getRerankCount());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.KEEPRANKCOUNT), query -> query.getRanking().getKeepRankCount());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.RANKSCOREDROPLIMIT), query -> query.getRanking().getRankScoreDropLimit());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.LIST_FEATURES), query -> query.getRanking().getListFeatures());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ATTRIBUTE), query -> query.getRanking().getMatchPhase().getAttribute());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.ASCENDING), query -> query.getRanking().getMatchPhase().getAscending());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_HITS), query -> query.getRanking().getMatchPhase().getMaxHits());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, MatchPhase.MAX_FILTER_COVERAGE), query -> query.getRanking().getMatchPhase().getMaxFilterCoverage());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.ATTRIBUTE), query -> query.getRanking().getMatchPhase().getDiversity().getAttribute());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.MINGROUPS), query -> query.getRanking().getMatchPhase().getDiversity().getMinGroups());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.FACTOR), query -> query.getRanking().getMatchPhase().getDiversity().getCutoffFactor());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.MATCH_PHASE, Ranking.DIVERSITY, Diversity.CUTOFF, Diversity.STRATEGY), query -> query.getRanking().getMatchPhase().getDiversity().getCutoffStrategy());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.ENABLE), query -> query.getRanking().getSoftTimeout().getEnable());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.FACTOR), query -> query.getRanking().getSoftTimeout().getFactor());
        map.put(CompoundName.fromComponents(Ranking.RANKING, Ranking.SOFTTIMEOUT, SoftTimeout.TAILCOST), query -> query.getRanking().getSoftTimeout().getTailcost());
        map.put(CompoundName.fromComponents(Select.SELECT), query -> query.getSelect().getGroupingExpressionString());
        map.put(CompoundName.fromComponents(Select.SELECT, Select.WHERE), query -> query.getSelect().getWhereString());
        map.put(CompoundName.fromComponents(Select.SELECT, Select.GROUPING), query -> query.getSelect().getGroupingString());
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.LEVEL), query -> query.getTrace().getLevel());
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.EXPLAIN_LEVEL), query -> query.getTrace().getExplainLevel());
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.TIMESTAMPS), query -> query.getTrace().getTimestamps());
        map.put(CompoundName.fromComponents(Trace.TRACE, Trace.QUERY), query -> query.getTrace().getQuery());
        map.put(CompoundName.fromComponents(Model.MODEL), Query::getModel);
        map.put(CompoundName.fromComponents(Ranking.RANKING), Query::getRanking);
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION), Query::getPresentation);

        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.BOLDING), query -> query.getPresentation().getBolding());
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY), query -> query.getPresentation().getSummary());
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT), query -> query.getPresentation().getFormat());
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.TIMING), query -> query.getPresentation().getTiming());
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY_FIELDS), query -> query.getPresentation().getSummaryFields());
        map.put(CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT, Presentation.TENSORS), query -> query.getPresentation().getTensorShortForm());

        map.put(Query.HITS, Query::getHits);
        map.put(Query.OFFSET, Query::getOffset);
        map.put(Query.TIMEOUT, Query::getTimeout);
        map.put(Query.NO_CACHE, Query::getNoCache);
        map.put(Query.GROUPING_SESSION_CACHE, Query::getGroupingSessionCache);
        return map;
    };

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
        GetProperty getter = getMap.get(key);
        if (getter != null) return getter.get(query);

        if (key.first().equals(Ranking.RANKING)) {
            Ranking ranking = query.getRanking();
            if (key.size() == 3 && key.get(1).equals(Ranking.MATCHING)) {
                Matching matching = ranking.getMatching();
                if (equalsWithLowerCaseAlias(key.last(), Matching.TERMWISELIMIT)) return matching.getTermwiseLimit();
                if (equalsWithLowerCaseAlias(key.last(), Matching.NUMTHREADSPERSEARCH)) return matching.getNumThreadsPerSearch();
                if (equalsWithLowerCaseAlias(key.last(), Matching.NUMSEARCHPARTITIIONS)) return matching.getNumSearchPartitions();
                if (equalsWithLowerCaseAlias(key.last(), Matching.MINHITSPERTHREAD)) return matching.getMinHitsPerThread();

            }
            else if (key.size() > 2) {
                // pass the portion after "ranking.features/properties" down
                if (key.get(1).equals(Ranking.FEATURES)) return ranking.getFeatures().getObject(key.rest().rest().toString());
                if (key.get(1).equals(Ranking.PROPERTIES)) return ranking.getProperties().get(key.rest().rest().toString());
            }
        }

        return super.get(key, context, substitution);
    }

    private void setInternal(CompoundName key, Object value, Map<String,String> context) {
        SetProperty setter = setMap.get(key);
        if (setter != null) {
            setter.set(query, value);
            return;
        }
        // TODO: Simplify error handling
        if (key.size() == 2 && key.first().equals(Model.MODEL)) {
            throwIllegalParameter(key.last(), Model.MODEL);
        }
        else if (key.first().equals(Ranking.RANKING)) {
            Ranking ranking = query.getRanking();
            if (key.size() == 2) {
                throwIllegalParameter(key.last(), Ranking.RANKING);
            }
            else if (key.size() >= 3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                if (key.size() == 3) {
                    throwIllegalParameter(key.rest().toString(), Ranking.MATCH_PHASE);
                } else if (key.get(2).equals(Ranking.DIVERSITY)) {
                    if ((key.size() > 4) && key.get(3).equals(Diversity.CUTOFF)) {
                        throwIllegalParameter(key.rest().toString(), Diversity.CUTOFF);
                    } else {
                        throwIllegalParameter(key.rest().toString(), Ranking.DIVERSITY);
                    }
                }
            }
            else if (key.size() == 3 && key.get(1).equals(Ranking.SOFTTIMEOUT)) {
                throwIllegalParameter(key.rest().toString(), Ranking.SOFTTIMEOUT);
            }
            else if (key.size() == 3 && key.get(1).equals(Ranking.MATCHING)) {
                Matching matching = ranking.getMatching();
                if (equalsWithLowerCaseAlias(key.last(), Matching.TERMWISELIMIT))
                    matching.setTermwiselimit(asDouble(value, 1.0));
                else if (equalsWithLowerCaseAlias(key.last(), Matching.NUMTHREADSPERSEARCH))
                    matching.setNumThreadsPerSearch(asInteger(value, 1));
                else if (equalsWithLowerCaseAlias(key.last(), Matching.NUMSEARCHPARTITIIONS))
                    matching.setNumSearchPartitions(asInteger(value, 1));
                else if (equalsWithLowerCaseAlias(key.last(), Matching.MINHITSPERTHREAD))
                    matching.setMinHitsPerThread(asInteger(value, 0));
                else if (key.last().equals(Matching.POST_FILTER_THRESHOLD))
                    matching.setPostFilterThreshold(asDouble(value, 1.0));
                else if (key.last().equals(Matching.APPROXIMATE_THRESHOLD))
                    matching.setApproximateThreshold(asDouble(value, 0.05));
                else
                    throwIllegalParameter(key.rest().toString(), Ranking.MATCHING);
            }
            else if (key.size() > 2) {
                String restKey = key.rest().rest().toString();
                chained().requireSettable(key, value, context);
                if (key.get(1).equals(Ranking.FEATURES))
                    setRankFeature(query, restKey, toSpecifiedType(restKey, value,
                            profileRegistry.getTypeRegistry().getComponent("features"),
                            context));
                else if (key.get(1).equals(Ranking.PROPERTIES))
                    ranking.getProperties().put(restKey, toSpecifiedType(restKey, value,
                            profileRegistry.getTypeRegistry().getComponent("properties"),
                            context));
                else
                    throwIllegalParameter(key.rest().toString(), Ranking.RANKING);
            }
        }
        else if (key.first().equals(Presentation.PRESENTATION)) {
            if (key.size() == 2) {
                throwIllegalParameter(key.last(), Presentation.PRESENTATION);
            }
            else if (key.size() == 3 && key.get(1).equals(Presentation.FORMAT)) {
                throwIllegalParameter(key.last(), Presentation.FORMAT);
            }
            else
                throwIllegalParameter(key.last(), Presentation.PRESENTATION);
        }
        else if ((key.size() == 4) &&
                key.get(0).equals(Trace.TRACE) &&
                key.get(1).equals(Trace.PROFILING) &&
                key.get(3).equals(ProfilingParams.DEPTH)) {
            var params = getProfilingParams(query.getTrace().getProfiling(), key.get(2));
            if (params != null) {
                params.setDepth(asInteger(value, 0));
            }
        }
        else if (key.first().equals(Select.SELECT)) {
            if (key.size() == 2) {
                throwIllegalParameter(key.rest().toString(), Select.SELECT);
            } else {
                throwIllegalParameter(key.last(), Select.SELECT);
            }
        }
        else {
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

    private static ProfilingParams getProfilingParams(Profiling prof, String name) {
        return switch (name) {
            case Profiling.MATCHING -> prof.getMatching();
            case Profiling.FIRST_PHASE_RANKING -> prof.getFirstPhaseRanking();
            case Profiling.SECOND_PHASE_RANKING -> prof.getSecondPhaseRanking();
            default -> null;
        };
    }

    @Override
    public Map<String, Object> listProperties(CompoundName prefix,
                                              Map<String,String> context,
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

    private void throwIllegalParameter(String key,String namespace) {
        throw new IllegalInputException("'" + key + "' is not a valid property in '" + namespace +
                                        "'. See the query api for valid keys starting by '" + namespace + "'.");
    }

    private boolean equalsWithLowerCaseAlias(String key, String property) {
        // The lowercase alias is used to provide backwards compatibility of a query property that was wrongly named in the first place.
        return key.equals(property) || key.equals(property.toLowerCase());
    }

    @Override
    public final Query getParentQuery() {
        return query;
    }

}
