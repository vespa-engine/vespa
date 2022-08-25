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

    private Query query;
    private final CompiledQueryProfileRegistry profileRegistry;
    private final Map<String, Embedder> embedders;

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
        if (key.size() == 2 && key.first().equals(Model.MODEL)) {
            Model model = query.getModel();
            if (key.last().equals(Model.QUERY_STRING)) return model.getQueryString();
            if (key.last().equals(Model.TYPE)) return model.getType();
            if (key.last().equals(Model.FILTER)) return model.getFilter();
            if (key.last().equals(Model.DEFAULT_INDEX)) return model.getDefaultIndex();
            if (key.last().equals(Model.LANGUAGE)) return model.getLanguage();
            if (key.last().equals(Model.LOCALE)) return model.getLocale();
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
                if (key.last().equals(Ranking.RERANKCOUNT)) return ranking.getRerankCount();
                if (key.last().equals(Ranking.LIST_FEATURES)) return ranking.getListFeatures();
            }
            else if (key.size() >= 3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
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
        else if (key.first().equals(Select.SELECT)) {
            if (key.size() == 1) {
                return query.getSelect().getGroupingExpressionString();
            }
            else if (key.size() == 2) {
                if (key.last().equals(Select.WHERE)) return query.getSelect().getWhereString();
                if (key.last().equals(Select.GROUPING)) return query.getSelect().getGroupingString();
            }
        }
        else if (key.first().equals(Presentation.PRESENTATION)) {
            if (key.size() == 2) {
                if (key.last().equals(Presentation.BOLDING)) return query.getPresentation().getBolding();
                if (key.last().equals(Presentation.SUMMARY)) return query.getPresentation().getSummary();
                if (key.last().equals(Presentation.FORMAT)) return query.getPresentation().getFormat();
                if (key.last().equals(Presentation.TIMING)) return query.getPresentation().getTiming();
                if (key.last().equals(Presentation.SUMMARY_FIELDS)) return query.getPresentation().getSummaryFields();
            } else if (key.size() == 3 && key.get(1).equals(Presentation.FORMAT)) {
                if (key.last().equals(Presentation.TENSORS)) return query.getPresentation().getTensorShortForm();
            }
        }
        else if (key.size() == 2 && key.first().equals(Trace.TRACE)) {
            if (key.last().equals(Trace.LEVEL)) return query.getTrace().getLevel();
            if (key.last().equals(Trace.EXPLAIN_LEVEL)) return query.getTrace().getExplainLevel();
            if (key.last().equals(Trace.TIMESTAMPS)) return query.getTrace().getTimestamps();
            if (key.last().equals(Trace.QUERY)) return query.getTrace().getQuery();
        }
        else if (key.size() == 1) {
            if (key.equals(Query.HITS)) return query.getHits();
            if (key.equals(Query.OFFSET)) return query.getOffset();
            if (key.equals(Query.TIMEOUT)) return query.getTimeout();
            if (key.equals(Query.NO_CACHE)) return query.getNoCache();
            if (key.equals(Query.GROUPING_SESSION_CACHE)) return query.getGroupingSessionCache();
            if (key.toString().equals(Model.MODEL)) return query.getModel();
            if (key.toString().equals(Ranking.RANKING)) return query.getRanking();
            if (key.toString().equals(Presentation.PRESENTATION)) return query.getPresentation();
        }

        return super.get(key, context, substitution);
    }

    @Override
    public void set(CompoundName key, Object value, Map<String,String> context) {
        // Note: The defaults here are never used
        try {
            if (key.size() == 2 && key.first().equals(Model.MODEL)) {
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
                else if (key.last().equals(Model.LOCALE))
                    model.setLocale(asString(value, ""));
                else if (key.last().equals(Model.ENCODING))
                    model.setEncoding(asString(value,""));
                else if (key.last().equals(Model.SEARCH_PATH))
                    model.setSearchPath(asString(value,""));
                else if (key.last().equals(Model.SOURCES))
                    model.setSources(asString(value,""));
                else if (key.last().equals(Model.RESTRICT))
                    model.setRestrict(asString(value,""));
                else
                    throwIllegalParameter(key.last(), Model.MODEL);
            }
            else if (key.first().equals(Ranking.RANKING)) {
                Ranking ranking = query.getRanking();
                if (key.size() == 2) {
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
                    else if (key.last().equals(Ranking.RERANKCOUNT))
                        ranking.setRerankCount(asInteger(value, null));
                    else if (key.last().equals(Ranking.LIST_FEATURES))
                        ranking.setListFeatures(asBoolean(value,false));
                    else
                        throwIllegalParameter(key.last(), Ranking.RANKING);
                }
                else if (key.size() >= 3 && key.get(1).equals(Ranking.MATCH_PHASE)) {
                    if (key.size() == 3) {
                        MatchPhase matchPhase = ranking.getMatchPhase();
                        if (key.last().equals(MatchPhase.ATTRIBUTE))
                            matchPhase.setAttribute(asString(value, null));
                        else if (key.last().equals(MatchPhase.ASCENDING))
                            matchPhase.setAscending(asBoolean(value, false));
                        else if (key.last().equals(MatchPhase.MAX_HITS))
                            matchPhase.setMaxHits(asLong(value, null));
                        else if (key.last().equals(MatchPhase.MAX_FILTER_COVERAGE))
                            matchPhase.setMaxFilterCoverage(asDouble(value, 0.2));
                        else
                            throwIllegalParameter(key.rest().toString(), Ranking.MATCH_PHASE);
                    }
                    else if (key.size() > 3 && key.get(2).equals(Ranking.DIVERSITY)) {
                        Diversity diversity = ranking.getMatchPhase().getDiversity();
                        if (key.last().equals(Diversity.ATTRIBUTE)) {
                            diversity.setAttribute(asString(value, null));
                        }
                        else if (key.last().equals(Diversity.MINGROUPS)) {
                            diversity.setMinGroups(asLong(value, null));
                        }
                        else if ((key.size() > 4) && key.get(3).equals(Diversity.CUTOFF)) {
                            if (key.last().equals(Diversity.FACTOR))
                                diversity.setCutoffFactor(asDouble(value, 10.0));
                            else if (key.last().equals(Diversity.STRATEGY))
                                diversity.setCutoffStrategy(asString(value, "loose"));
                            else
                                throwIllegalParameter(key.rest().toString(), Diversity.CUTOFF);
                        }
                        else {
                            throwIllegalParameter(key.rest().toString(), Ranking.DIVERSITY);
                        }
                    }
                }
                else if (key.size() == 3 && key.get(1).equals(Ranking.SOFTTIMEOUT)) {
                    SoftTimeout soft = ranking.getSoftTimeout();
                    if (key.last().equals(SoftTimeout.ENABLE))
                        soft.setEnable(asBoolean(value, true));
                    else if (key.last().equals(SoftTimeout.FACTOR))
                        soft.setFactor(asDouble(value, null));
                    else if (key.last().equals(SoftTimeout.TAILCOST))
                        soft.setTailcost(asDouble(value, null));
                    else
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
                        setRankFeature(query, restKey, toSpecifiedType(restKey,
                                                                       value,
                                                                       profileRegistry.getTypeRegistry().getComponent("features"),
                                                                       context));
                    else if (key.get(1).equals(Ranking.PROPERTIES))
                        ranking.getProperties().put(restKey, toSpecifiedType(restKey,
                                                                             value,
                                                                             profileRegistry.getTypeRegistry().getComponent("properties"),
                                                                             context));
                    else
                        throwIllegalParameter(key.rest().toString(), Ranking.RANKING);
                }
            }
            else if (key.first().equals(Presentation.PRESENTATION)) {
                if (key.size() == 2) {
                    if (key.last().equals(Presentation.BOLDING))
                        query.getPresentation().setBolding(asBoolean(value, true));
                    else if (key.last().equals(Presentation.SUMMARY))
                        query.getPresentation().setSummary(asString(value, ""));
                    else if (key.last().equals(Presentation.FORMAT))
                        query.getPresentation().setFormat(asString(value, ""));
                    else if (key.last().equals(Presentation.TIMING))
                        query.getPresentation().setTiming(asBoolean(value, true));
                    else if (key.last().equals(Presentation.SUMMARY_FIELDS))
                        query.getPresentation().setSummaryFields(asString(value, ""));
                    else
                        throwIllegalParameter(key.last(), Presentation.PRESENTATION);
                }
                else if (key.size() == 3 && key.get(1).equals(Presentation.FORMAT)) {
                    if (key.last().equals(Presentation.TENSORS))
                        query.getPresentation().setTensorShortForm(asString(value, "short"));
                    else
                        throwIllegalParameter(key.last(), Presentation.FORMAT);
                }
                else
                    throwIllegalParameter(key.last(), Presentation.PRESENTATION);
            }
            else if (key.size() == 2 && key.first().equals(Trace.TRACE)) {
                if (key.last().equals(Trace.LEVEL))
                    query.getTrace().setLevel(asInteger(value, 0));
                if (key.last().equals(Trace.EXPLAIN_LEVEL))
                    query.getTrace().setExplainLevel(asInteger(value, 0));
                if (key.last().equals(Trace.PROFILE_DEPTH))
                    query.getTrace().setProfileDepth(asInteger(value, 0));
                if (key.last().equals(Trace.TIMESTAMPS))
                    query.getTrace().setTimestamps(asBoolean(value, false));
                if (key.last().equals(Trace.QUERY))
                    query.getTrace().setQuery(asBoolean(value, true));
            }
            else if (key.first().equals(Select.SELECT)) {
                if (key.size() == 1) {
                    query.getSelect().setGroupingExpressionString(asString(value, ""));
                }
                else if (key.size() == 2) {
                    if (key.last().equals(Select.WHERE))
                        query.getSelect().setWhereString(asString(value, ""));
                    else if (key.last().equals(Select.GROUPING))
                        query.getSelect().setGroupingString(asString(value, ""));
                    else
                        throwIllegalParameter(key.rest().toString(), Select.SELECT);
                }
                else {
                    throwIllegalParameter(key.last(), Select.SELECT);
                }
            }
            else if (key.size() == 1) {
                if (key.equals(Query.HITS))
                    query.setHits(asInteger(value,10));
                else if (key.equals(Query.OFFSET))
                    query.setOffset(asInteger(value,0));
                else if (key.equals(Query.TIMEOUT))
                    query.setTimeout(value.toString());
                else if (key.equals(Query.NO_CACHE))
                    query.setNoCache(asBoolean(value,false));
                else if (key.equals(Query.GROUPING_SESSION_CACHE))
                    query.setGroupingSessionCache(asBoolean(value, true));
                else
                    super.set(key,value,context);
            }
            else {
                super.set(key, value, context);
            }
        }
        catch (Exception e) { // Make sure error messages are informative. This should be moved out of this properties implementation
            if (e.getMessage() != null && e.getMessage().startsWith("Could not set"))
                throw e;
            else
                throw new IllegalInputException("Could not set '" + key + "' to '" + value + "'", e);
        }
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
