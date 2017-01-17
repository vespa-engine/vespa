// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.properties;

import com.yahoo.component.ComponentId;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.query.*;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;
import com.yahoo.search.query.profile.types.QueryProfileTypeRegistry;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
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

    private static final String MODEL_PREFIX = Model.MODEL + ".";
    private static final String RANKING_PREFIX = Ranking.RANKING + ".";
    private static final String PRESENTATION_PREFIX = Presentation.PRESENTATION + ".";

    public static final CompoundName[] PER_SOURCE_QUERY_PROPERTIES = new CompoundName[] {
            new CompoundName(MODEL_PREFIX + Model.QUERY_STRING),
            new CompoundName(MODEL_PREFIX + Model.TYPE),
            new CompoundName(MODEL_PREFIX + Model.FILTER),
            new CompoundName(MODEL_PREFIX + Model.DEFAULT_INDEX),
            new CompoundName(MODEL_PREFIX + Model.LANGUAGE),
            new CompoundName(MODEL_PREFIX + Model.ENCODING),
            new CompoundName(MODEL_PREFIX + Model.SOURCES),
            new CompoundName(MODEL_PREFIX + Model.SEARCH_PATH),
            new CompoundName(MODEL_PREFIX + Model.RESTRICT),
            new CompoundName(RANKING_PREFIX + Ranking.LOCATION),
            new CompoundName(RANKING_PREFIX + Ranking.PROFILE),
            new CompoundName(RANKING_PREFIX + Ranking.SORTING),
            new CompoundName(RANKING_PREFIX + Ranking.FRESHNESS),
            new CompoundName(RANKING_PREFIX + Ranking.QUERYCACHE),
            new CompoundName(RANKING_PREFIX + Ranking.LIST_FEATURES),
            new CompoundName(PRESENTATION_PREFIX + Presentation.BOLDING),
            new CompoundName(PRESENTATION_PREFIX + Presentation.SUMMARY),
            new CompoundName(PRESENTATION_PREFIX + Presentation.REPORT_COVERAGE),
            new CompoundName(PRESENTATION_PREFIX + Presentation.FORMAT),
            new CompoundName(PRESENTATION_PREFIX + Presentation.SUMMARY_FIELDS),
            Query.HITS,
            Query.OFFSET,
            Query.TRACE_LEVEL,
            Query.TIMEOUT,
            Query.NO_CACHE,
            Query.GROUPING_SESSION_CACHE };

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
        if (key.size()==2 && key.first().equals(Model.MODEL)) {
            if (key.last().equals(Model.QUERY_STRING)) return query.getModel().getQueryString();
            if (key.last().equals(Model.TYPE)) return query.getModel().getType();
            if (key.last().equals(Model.FILTER)) return query.getModel().getFilter();
            if (key.last().equals(Model.DEFAULT_INDEX)) return query.getModel().getDefaultIndex();
            if (key.last().equals(Model.LANGUAGE)) return query.getModel().getLanguage();
            if (key.last().equals(Model.ENCODING)) return query.getModel().getEncoding();
            if (key.last().equals(Model.SOURCES)) return query.getModel().getSources();
            if (key.last().equals(Model.SEARCH_PATH)) return query.getModel().getSearchPath();
            if (key.last().equals(Model.RESTRICT)) return query.getModel().getRestrict();
        }
        else if (key.first().equals(Ranking.RANKING)) {
            if (key.size()==2) {
                if (key.last().equals(Ranking.LOCATION)) return query.getRanking().getLocation();
                if (key.last().equals(Ranking.PROFILE)) return query.getRanking().getProfile();
                if (key.last().equals(Ranking.SORTING)) return query.getRanking().getSorting();
                if (key.last().equals(Ranking.FRESHNESS)) return query.getRanking().getFreshness();
                if (key.last().equals(Ranking.QUERYCACHE)) return query.getRanking().getQueryCache();
                if (key.last().equals(Ranking.LIST_FEATURES)) return query.getRanking().getListFeatures();
            }
            else if (key.size()>=3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                if (key.size() == 3) {
                    MatchPhase matchPhase = query.getRanking().getMatchPhase();
                    if (key.last().equals(MatchPhase.ATTRIBUTE)) return matchPhase.getAttribute();
                    if (key.last().equals(MatchPhase.ASCENDING)) return matchPhase.getAscending();
                    if (key.last().equals(MatchPhase.MAX_HITS)) return matchPhase.getMaxHits();
                    if (key.last().equals(MatchPhase.MAX_FILTER_COVERAGE)) return matchPhase.getMaxFilterCoverage();
                } else if (key.size() >= 4 && key.get(2).equals(Ranking.DIVERSITY)) {
                    Diversity diversity = query.getRanking().getMatchPhase().getDiversity();
                    if (key.size() == 4) {
                        if (key.last().equals(Diversity.ATTRIBUTE)) return diversity.getAttribute();
                        if (key.last().equals(Diversity.MINGROUPS)) return diversity.getMinGroups();
                    } else if ((key.size() == 5)  && key.get(3).equals(Diversity.CUTOFF)) {
                        if (key.last().equals(Diversity.FACTOR)) return diversity.getCutoffFactor();
                        if (key.last().equals(Diversity.STRATEGY)) return diversity.getCutoffStrategy();
                    }
                }
            }
            else if (key.size()>2) {
                // pass the portion after "ranking.features/properties" down
                if (key.get(1).equals(Ranking.FEATURES)) return query.getRanking().getFeatures().getObject(key.rest().rest().toString());
                if (key.get(1).equals(Ranking.PROPERTIES)) return query.getRanking().getProperties().get(key.rest().rest().toString());
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
        return super.get(key,context,substitution);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void set(CompoundName key, Object value, Map<String,String> context) {
        // Note: The defaults here are never used
        try {
            if (key.size()==2 && key.first().equals(Model.MODEL)) {
                if (key.last().equals(Model.QUERY_STRING))
                    query.getModel().setQueryString(asString(value, ""));
                else if (key.last().equals(Model.TYPE))
                    query.getModel().setType(asString(value, "ANY"));
                else if (key.last().equals(Model.FILTER))
                    query.getModel().setFilter(asString(value, ""));
                else if (key.last().equals(Model.DEFAULT_INDEX))
                    query.getModel().setDefaultIndex(asString(value, ""));
                else if (key.last().equals(Model.LANGUAGE))
                    query.getModel().setLanguage(asString(value, ""));
                else if (key.last().equals(Model.ENCODING))
                    query.getModel().setEncoding(asString(value,""));
                else if (key.last().equals(Model.SEARCH_PATH))
                    query.getModel().setSearchPath(asString(value,""));
                else if (key.last().equals(Model.SOURCES))
                    query.getModel().setSources(asString(value,""));
                else if (key.last().equals(Model.RESTRICT))
                    query.getModel().setRestrict(asString(value,""));
                else
                    throwIllegalParameter(key.last(),Model.MODEL);
            }
            else if (key.first().equals(Ranking.RANKING)) {
                if (key.size()==2) {
                    if (key.last().equals(Ranking.LOCATION))
                        query.getRanking().setLocation(asString(value,""));
                    else if (key.last().equals(Ranking.PROFILE))
                        query.getRanking().setProfile(asString(value,""));
                    else if (key.last().equals(Ranking.SORTING))
                        query.getRanking().setSorting(asString(value,""));
                    else if (key.last().equals(Ranking.FRESHNESS))
                        query.getRanking().setFreshness(asString(value, ""));
                    else if (key.last().equals(Ranking.QUERYCACHE))
                        query.getRanking().setQueryCache(asBoolean(value, false));
                    else if (key.last().equals(Ranking.LIST_FEATURES))
                        query.getRanking().setListFeatures(asBoolean(value,false));
                }
                else if (key.size()>=3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                    if (key.size() == 3) {
                        MatchPhase matchPhase = query.getRanking().getMatchPhase();
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
                        Diversity diversity = query.getRanking().getMatchPhase().getDiversity();
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
                else if (key.size()>2) {
                    String restKey = key.rest().rest().toString();
                    if (key.get(1).equals(Ranking.FEATURES))
                        setRankingFeature(query, restKey, toSpecifiedType(restKey, value, profileRegistry.getTypeRegistry().getComponent("features")));
                    else if (key.get(1).equals(Ranking.PROPERTIES))
                        query.getRanking().getProperties().put(restKey, toSpecifiedType(restKey, value, profileRegistry.getTypeRegistry().getComponent("properties")));
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
