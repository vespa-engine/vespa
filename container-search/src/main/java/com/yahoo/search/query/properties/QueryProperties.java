// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.*;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.ranking.Matching;
import com.yahoo.search.query.ranking.SoftTimeout;
import com.yahoo.tensor.Tensor;

import java.util.Map;

/**
 * Maps between the query model and text properties.
 * This can be done simpler by using reflection but the performance penalty was not worth it,
 * especially since we should be conservative in adding things to the query model.
 *
 * @author bratseth
 */
public class QueryProperties extends Properties {

    public static final CompoundName[] PER_SOURCE_QUERY_PROPERTIES = new CompoundName[] {
            Query.HITS,
            Query.OFFSET,
            Query.TRACE_LEVEL,
            Query.TIMEOUT,
            Query.NO_CACHE,
            Query.GROUPING_SESSION_CACHE,
            CompoundName.fromComponents(Model.MODEL, Model.QUERY_STRING),
            CompoundName.fromComponents(Model.MODEL, Model.TYPE),
            CompoundName.fromComponents(Model.MODEL, Model.FILTER),
            CompoundName.fromComponents(Model.MODEL, Model.DEFAULT_INDEX),
            CompoundName.fromComponents(Model.MODEL, Model.LANGUAGE),
            CompoundName.fromComponents(Model.MODEL, Model.ENCODING),
            CompoundName.fromComponents(Model.MODEL, Model.SOURCES),
            CompoundName.fromComponents(Model.MODEL, Model.SEARCH_PATH),
            CompoundName.fromComponents(Model.MODEL, Model.RESTRICT),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.LOCATION),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.PROFILE),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.SORTING),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.FRESHNESS),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.QUERYCACHE),
            CompoundName.fromComponents(Ranking.RANKING, Ranking.LIST_FEATURES),
            CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.BOLDING),
            CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY),
            CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.REPORT_COVERAGE),
            CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.FORMAT),
            CompoundName.fromComponents(Presentation.PRESENTATION, Presentation.SUMMARY_FIELDS)};

    private Query query;
    private final CompiledQueryProfileRegistry profileRegistry;

    public QueryProperties(Query query, CompiledQueryProfileRegistry profileRegistry) {
        this.query = query;
        this.profileRegistry = profileRegistry;
    }

    public void setParentQuery(Query query) {
        this.query=query;
        super.setParentQuery(query);
    }

    @SuppressWarnings("deprecation")
    @Override
    public Object get(CompoundName key, Map<String,String> context,
                      com.yahoo.processing.request.Properties substitution) {
        if (key.size() == 2 && key.first().equals(Model.MODEL)) {
            Model model = query.getModel();
            if (key.last().equals(Model.QUERY_STRING)) return model.getQueryString();
            if (key.last().equals(Model.TYPE)) return model.getType();
            if (key.last().equals(Model.FILTER)) return model.getFilter();
            if (key.last().equals(Model.DEFAULT_INDEX)) return model.getDefaultIndex();
            if (key.last().equals(Model.LANGUAGE)) return model.getLanguage();
            if (key.last().equals(Model.ENCODING)) return model.getEncoding();
            if (key.last().equals(Model.SOURCES)) return model.getSources();
            if (key.last().equals(Model.SEARCH_PATH)) return model.getSearchPath();
            if (key.last().equals(Model.RESTRICT)) return model.getRestrict();
        }
        else if (key.first().equals(Ranking.RANKING)) {
            Ranking ranking = query.getRanking();
            if (key.size() == 2) {
                if (key.last().equals(Ranking.LOCATION)) return ranking.getLocation();
                if (key.last().equals(Ranking.PROFILE)) return ranking.getProfile();
                if (key.last().equals(Ranking.SORTING)) return ranking.getSorting();
                if (key.last().equals(Ranking.FRESHNESS)) return ranking.getFreshness();
                if (key.last().equals(Ranking.QUERYCACHE)) return ranking.getQueryCache();
                if (key.last().equals(Ranking.LIST_FEATURES)) return ranking.getListFeatures();
            }
            else if (key.size()>=3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                if (key.size() == 3) {
                    MatchPhase matchPhase = ranking.getMatchPhase();
                    if (key.last().equals(MatchPhase.ATTRIBUTE)) return matchPhase.getAttribute();
                    if (key.last().equals(MatchPhase.ASCENDING)) return matchPhase.getAscending();
                    if (key.last().equals(MatchPhase.MAX_HITS)) return matchPhase.getMaxHits();
                    if (key.last().equals(MatchPhase.MAX_FILTER_COVERAGE)) return matchPhase.getMaxFilterCoverage();
                } else if (key.size() >= 4 && key.get(2).equals(Ranking.DIVERSITY)) {
                    Diversity diversity = ranking.getMatchPhase().getDiversity();
                    if (key.size() == 4) {
                        if (key.last().equals(Diversity.ATTRIBUTE)) return diversity.getAttribute();
                        if (key.last().equals(Diversity.MINGROUPS)) return diversity.getMinGroups();
                    } else if ((key.size() == 5)  && key.get(3).equals(Diversity.CUTOFF)) {
                        if (key.last().equals(Diversity.FACTOR)) return diversity.getCutoffFactor();
                        if (key.last().equals(Diversity.STRATEGY)) return diversity.getCutoffStrategy();
                    }
                }
            }
            else if (key.size() == 3 && key.get(1).equals(Ranking.SOFTTIMEOUT)) {
                SoftTimeout soft = ranking.getSoftTimeout();
                if (key.last().equals(SoftTimeout.ENABLE)) return soft.getEnable();
                if (key.last().equals(SoftTimeout.FACTOR)) return soft.getFactor();
                if (key.last().equals(SoftTimeout.TAILCOST)) return soft.getTailcost();
            }
            else if (key.size() == 3 && key.get(1).equals(Ranking.MATCHING)) {
                Matching matching = ranking.getMatching();
                if (key.last().equals(Matching.TERMWISELIMIT)) return matching.getTermwiseLimit();
                if (key.last().equals(Matching.NUMTHREADSPERSEARCH)) return matching.getNumThreadsPerSearch();
                if (key.last().equals(Matching.NUMSEARCHPARTITIIONS)) return matching.getNumSearchPartitions();
                if (key.last().equals(Matching.MINHITSPERTHREAD)) return matching.getMinHitsPerThread();

            }
            else if (key.size()>2) {
                // pass the portion after "ranking.features/properties" down
                if (key.get(1).equals(Ranking.FEATURES)) return ranking.getFeatures().getObject(key.rest().rest().toString());
                if (key.get(1).equals(Ranking.PROPERTIES)) return ranking.getProperties().get(key.rest().rest().toString());
            }
        }
        else if (key.size()==2 && key.first().equals(Presentation.PRESENTATION)) {
            if (key.last().equals(Presentation.BOLDING)) return query.getPresentation().getBolding();
            if (key.last().equals(Presentation.SUMMARY)) return query.getPresentation().getSummary();
            if (key.last().equals(Presentation.REPORT_COVERAGE)) return true; // TODO: Remove this line on Vespa 7
            if (key.last().equals(Presentation.FORMAT)) return query.getPresentation().getFormat();
            if (key.last().equals(Presentation.TIMING)) return query.getPresentation().getTiming();
            if (key.last().equals(Presentation.SUMMARY_FIELDS)) return query.getPresentation().getSummaryFields();
        }
        else if (key.first().equals("rankfeature") || key.first().equals("featureoverride")) { // featureoverride is deprecated
            return query.getRanking().getFeatures().getObject(key.rest().toString());
        } else if (key.first().equals("rankproperty")) {
            return query.getRanking().getProperties().get(key.rest().toString());
        } else if (key.size()==1) {
            if (key.equals(Query.HITS)) return query.getHits();
            if (key.equals(Query.OFFSET)) return query.getOffset();
            if (key.equals(Query.TRACE_LEVEL)) return query.getTraceLevel();
            if (key.equals(Query.TIMEOUT)) return query.getTimeout();
            if (key.equals(Query.NO_CACHE)) return query.getNoCache();
            if (key.equals(Query.GROUPING_SESSION_CACHE)) return query.getGroupingSessionCache();
            if (key.toString().equals(Model.MODEL)) return query.getModel();
            if (key.toString().equals(Ranking.RANKING)) return query.getRanking();
            if (key.toString().equals(Presentation.PRESENTATION)) return query.getPresentation();
        }
        return super.get(key, context, substitution);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void set(CompoundName key, Object value, Map<String,String> context) {
        // Note: The defaults here are never used
        try {
            if (key.size()==2 && key.first().equals(Model.MODEL)) {
                Model model = query.getModel();
                if (key.last().equals(Model.QUERY_STRING))
                    model.setQueryString(asString(value, ""));
                else if (key.last().equals(Model.TYPE))
                    model.setType(asString(value, "ANY"));
                else if (key.last().equals(Model.FILTER))
                    model.setFilter(asString(value, ""));
                else if (key.last().equals(Model.DEFAULT_INDEX))
                    model.setDefaultIndex(asString(value, ""));
                else if (key.last().equals(Model.LANGUAGE))
                    model.setLanguage(asString(value, ""));
                else if (key.last().equals(Model.ENCODING))
                    model.setEncoding(asString(value,""));
                else if (key.last().equals(Model.SEARCH_PATH))
                    model.setSearchPath(asString(value,""));
                else if (key.last().equals(Model.SOURCES))
                    model.setSources(asString(value,""));
                else if (key.last().equals(Model.RESTRICT))
                    model.setRestrict(asString(value,""));
                else
                    throwIllegalParameter(key.last(),Model.MODEL);
            }
            else if (key.first().equals(Ranking.RANKING)) {
                Ranking ranking = query.getRanking();
                if (key.size()==2) {
                    if (key.last().equals(Ranking.LOCATION))
                        ranking.setLocation(asString(value,""));
                    else if (key.last().equals(Ranking.PROFILE))
                        ranking.setProfile(asString(value,""));
                    else if (key.last().equals(Ranking.SORTING))
                        ranking.setSorting(asString(value,""));
                    else if (key.last().equals(Ranking.FRESHNESS))
                        ranking.setFreshness(asString(value, ""));
                    else if (key.last().equals(Ranking.QUERYCACHE))
                        ranking.setQueryCache(asBoolean(value, false));
                    else if (key.last().equals(Ranking.LIST_FEATURES))
                        ranking.setListFeatures(asBoolean(value,false));
                }
                else if (key.size()>=3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                    if (key.size() == 3) {
                        MatchPhase matchPhase = ranking.getMatchPhase();
                        if (key.last().equals(MatchPhase.ATTRIBUTE)) {
                            matchPhase.setAttribute(asString(value, null));
                        } else if (key.last().equals(MatchPhase.ASCENDING)) {
                            matchPhase.setAscending(asBoolean(value, false));
                        } else if (key.last().equals(MatchPhase.MAX_HITS)) {
                            matchPhase.setMaxHits(asLong(value, null));
                        } else if (key.last().equals(MatchPhase.MAX_FILTER_COVERAGE)) {
                            matchPhase.setMaxFilterCoverage(asDouble(value, 0.2));
                        }
                    } else if (key.size() > 3 && key.get(2).equals(Ranking.DIVERSITY)) {
                        Diversity diversity = ranking.getMatchPhase().getDiversity();
                        if (key.last().equals(Diversity.ATTRIBUTE)) {
                            diversity.setAttribute(asString(value, null));
                        } else if (key.last().equals(Diversity.MINGROUPS)) {
                            diversity.setMinGroups(asLong(value, null));
                        } else if ((key.size() > 4) && key.get(3).equals(Diversity.CUTOFF)) {
                            if (key.last().equals(Diversity.FACTOR)) {
                                diversity.setCutoffFactor(asDouble(value, 10.0));
                            } else if (key.last().equals(Diversity.STRATEGY)) {
                                diversity.setCutoffStrategy(asString(value, "loose"));
                            }
                        }
                    }
                }
                else if (key.size() == 3 && key.get(1).equals(Ranking.SOFTTIMEOUT)) {
                    SoftTimeout soft = ranking.getSoftTimeout();
                    if (key.last().equals(SoftTimeout.ENABLE)) soft.setEnable(asBoolean(value, false));
                    if (key.last().equals(SoftTimeout.FACTOR)) soft.setFactor(asDouble(value, 0.50));
                    if (key.last().equals(SoftTimeout.TAILCOST)) soft.setTailcost(asDouble(value, 0.10));
                }
                else if (key.size() == 3 && key.get(1).equals(Ranking.MATCHING)) {
                    Matching matching = ranking.getMatching();
                    if (key.last().equals(Matching.TERMWISELIMIT)) matching.setTermwiselimit(asDouble(value, 1.0));
                    if (key.last().equals(Matching.NUMTHREADSPERSEARCH)) matching.setNumThreadsPerSearch(asInteger(value, 1));
                    if (key.last().equals(Matching.NUMSEARCHPARTITIIONS)) matching.setNumSearchPartitions(asInteger(value, 1));
                    if (key.last().equals(Matching.MINHITSPERTHREAD)) matching.setMinHitsPerThread(asInteger(value, 0));
                }
                else if (key.size()>2) {
                    String restKey = key.rest().rest().toString();
                    if (key.get(1).equals(Ranking.FEATURES))
                        setRankingFeature(query, restKey, toSpecifiedType(restKey, value, profileRegistry.getTypeRegistry().getComponent("features")));
                    else if (key.get(1).equals(Ranking.PROPERTIES))
                        ranking.getProperties().put(restKey, toSpecifiedType(restKey, value, profileRegistry.getTypeRegistry().getComponent("properties")));
                    else
                        throwIllegalParameter(key.rest().toString(),Ranking.RANKING);
                }
            }
            else if (key.size()==2 && key.first().equals(Presentation.PRESENTATION)) {
                if (key.last().equals(Presentation.BOLDING))
                    query.getPresentation().setBolding(asBoolean(value, true));
                else if (key.last().equals(Presentation.SUMMARY))
                    query.getPresentation().setSummary(asString(value, ""));
                else if (key.last().equals(Presentation.FORMAT))
                    query.getPresentation().setFormat(asString(value,""));
                else if (key.last().equals(Presentation.TIMING))
                    query.getPresentation().setTiming(asBoolean(value, true));
                else if (key.last().equals(Presentation.SUMMARY_FIELDS))
                    query.getPresentation().setSummaryFields(asString(value,""));
                else if ( ! key.last().equals(Presentation.REPORT_COVERAGE)) // TODO: Change this line to "else" on Vespa 7
                    throwIllegalParameter(key.last(), Presentation.PRESENTATION);
            }
            else if (key.first().equals("rankfeature") || key.first().equals("featureoverride") ) { // featureoverride is deprecated
                setRankingFeature(query, key.rest().toString(), toSpecifiedType(key.rest().toString(), value, profileRegistry.getTypeRegistry().getComponent("features")));
            } else if (key.first().equals("rankproperty")) {
                query.getRanking().getProperties().put(key.rest().toString(), toSpecifiedType(key.rest().toString(), value, profileRegistry.getTypeRegistry().getComponent("properties")));
            } else if (key.size()==1) {
                if (key.equals(Query.HITS))
                    query.setHits(asInteger(value,10));
                else if (key.equals(Query.OFFSET))
                    query.setOffset(asInteger(value,0));
                else if (key.equals(Query.TRACE_LEVEL))
                    query.setTraceLevel(asInteger(value,0));
                else if (key.equals(Query.TIMEOUT))
                    query.setTimeout(value.toString());
                else if (key.equals(Query.NO_CACHE))
                    query.setNoCache(asBoolean(value,false));
                else if (key.equals(Query.GROUPING_SESSION_CACHE))
                    query.setGroupingSessionCache(asBoolean(value, false));
                else
                    super.set(key,value,context);
            }
            else
                super.set(key,value,context);
        }
        catch (Exception e) { // Make sure error messages are informative. This should be moved out of this properties implementation
            if (e.getMessage().startsWith("Could not set"))
                throw e;
            else
                throw new IllegalArgumentException("Could not set '" + key + "' to '" + value + "'", e);
        }
    }

    @Override
    public Map<String, Object> listProperties(CompoundName prefix,
                                              Map<String,String> context,
                                              com.yahoo.processing.request.Properties substitution) {
        Map<String, Object> properties = super.listProperties(prefix, context, substitution);
        for (CompoundName queryProperty : PER_SOURCE_QUERY_PROPERTIES) {
            if (queryProperty.hasPrefix(prefix)) {
                Object value = this.get(queryProperty, context, substitution);
                if (value != null)
                    properties.put(queryProperty.toString(), value);
            }
        }
        return properties;
    }

    private void setRankingFeature(Query query, String key, Object value) {
        if (value instanceof Tensor)
            query.getRanking().getFeatures().put(key, (Tensor)value);
        else
            query.getRanking().getFeatures().put(key, asString(value, ""));
    }

    private Object toSpecifiedType(String key, Object value, QueryProfileType type) {
        if ( ! ( value instanceof String)) return value; // already typed
        if (type == null) return value; // no type info -> keep as string
        FieldDescription field = type.getField(key);
        if (field == null) return value; // ditto
        return field.getType().convertFrom(value, profileRegistry);
    }

    private void throwIllegalParameter(String key,String namespace) {
        throw new IllegalArgumentException("'" + key + "' is not a valid property in '" + namespace +
                                           "'. See the search api for valid keys starting by '" + namespace + "'.");
    }

    @Override
    public final Query getParentQuery() {
        return query;
    }
}
