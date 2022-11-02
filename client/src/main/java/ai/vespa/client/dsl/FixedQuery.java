// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * FixedQuery contains a 'Query'.
 * This object holds vespa or user defined parameters
 * https://docs.vespa.ai/en/reference/query-api-reference.html
 */
public class FixedQuery {

    private final EndQuery endQuery;
    private final Map<String, String> others = new HashMap<>();
    private Map<String, String> queryMap;

    FixedQuery(EndQuery endQuery) {
        this.endQuery = endQuery;
    }

    public FixedQuery hits(int hits) {
        this.param("hits", hits);
        return this;
    }

    public FixedQuery offset(int offset) {
        this.param("offset", offset);
        return this;
    }

    public FixedQuery queryProfile(String queryProfile) {
        this.param("queryProfile", queryProfile);
        return this;
    }

    public FixedQuery groupingSessionCache(boolean enable) {
        this.param("groupingSessionCache", enable);
        return this;
    }

    public FixedQuery searchChain(String searchChain) {
        this.param("searchChain", searchChain);
        return this;
    }

    public FixedQuery timeout(int second) {
        this.param("timeout", second);
        return this;
    }

    public FixedQuery timeoutInMs(int milli) {
        this.param("timeout", milli + "ms");
        return this;
    }

    public FixedQuery tracelevel(int level) {
        this.param("tracelevel", level);
        return this;
    }

    public FixedQuery traceTimestamps(boolean enable) {
        this.param("trace.timestamps", enable);
        return this;
    }

    public FixedQuery defaultIndex(String indexName) {
        this.param("default-index", indexName);
        return this;
    }

    public FixedQuery encoding(String encoding) {
        this.param("encoding", encoding);
        return this;
    }

    public FixedQuery filter(String filter) {
        this.param("filter", filter);
        return this;
    }

    public FixedQuery locale(String locale) {
        this.param("locale", locale);
        return this;
    }

    public FixedQuery language(String language) {
        this.param("language", language);
        return this;
    }

    public FixedQuery query(String query) {
        this.param("query", query);
        return this;
    }

    public FixedQuery restrict(String commaDelimitedDocTypeNames) {
        this.param("restrict", commaDelimitedDocTypeNames);
        return this;
    }

    public FixedQuery path(String searchPath) {
        this.param("path", searchPath);
        return this;
    }

    public FixedQuery sources(String commaDelimitedSourceNames) {
        this.param("sources", commaDelimitedSourceNames);
        return this;
    }

    public FixedQuery type(String type) {
        // web, all, any, phrase, yql, adv (deprecated)
        this.param("type", type);
        return this;
    }

    public FixedQuery location(String location) {
        this.param("location", location);
        return this;
    }

    public FixedQuery rankfeature(String featureName, String featureValue) {
        this.param("rankfeature." + featureName, featureValue);
        return this;
    }

    public FixedQuery rankfeatures(boolean enable) {
        this.param("rankfeatures", enable);
        return this;
    }

    public FixedQuery ranking(String rankProfileName) {
        this.param("ranking", rankProfileName);
        return this;
    }

    public FixedQuery rankproperty(String propertyName, String propertyValue) {
        this.param("rankproperty." + propertyName, propertyValue);
        return this;
    }

    public FixedQuery rankingSoftTimeout(boolean enable) {
        this.param("ranking.softtimeout.enable", enable);
        return this;
    }

    public FixedQuery rankingSoftTimeout(boolean enable, double factor) {
        this.param("ranking.softtimeout.enable", enable);
        this.param("ranking.softtimeout.factor", factor);
        return this;
    }

    public FixedQuery sorting(String sorting) {
        this.param("sorting", sorting);
        return this;
    }

    public FixedQuery rankingFreshness(String freshness) {
        this.param("ranking.freshness", freshness);
        return this;
    }

    public FixedQuery rankingQueryCache(boolean enable) {
        this.param("ranking.queryCache", enable);
        return this;
    }

    public FixedQuery bolding(boolean enable) {
        this.param("bolding", enable);
        return this;
    }

    public FixedQuery format(String format) {
        this.param("format", format);
        return this;
    }

    public FixedQuery summary(String summaryClass) {
        this.param("summary", summaryClass);
        return this;
    }

    public FixedQuery presentationTemplate(String template) {
        this.param("presentation.template", template);
        return this;
    }

    public FixedQuery presentationTiming(boolean enable) {
        this.param("presentation.timing", enable);
        return this;
    }

    public FixedQuery select(String groupSyntax) {
        this.param("select", groupSyntax);
        return this;
    }

    public FixedQuery select(Group group) {
        this.param("select", group.toString());
        return this;
    }

    public FixedQuery collapseField(String summaryFieldName) {
        this.param("collapsefield", summaryFieldName);
        return this;
    }

    public FixedQuery collapseSummary(String summaryClass) {
        this.param("collapse.summary", summaryClass);
        return this;
    }

    public FixedQuery collapseSize(int size) {
        this.param("collapsesize", size);
        return this;
    }

    public FixedQuery posLatLong(String vespaLatLong) {
        this.param("pos.ll", vespaLatLong);
        return this;
    }

    public FixedQuery posLatLong(double lat, double lon) {
        String latlong = toVespaLatLong(lat, lon);
        return posLatLong(latlong);
    }

    private String toVespaLatLong(double lat, double lon) {
        double absLat = Math.abs(lat);
        double absLon = Math.abs(lon);
        if (absLat > 90 || absLon > 180) {
            throw new IllegalArgumentException(Text.format("invalid lat long value, lat=%f, long=%f", lat, lon));
        }

        return Text.format("%s%f;%s%f",
                             lat > 0 ? "N" : "S", absLat,
                             lon > 0 ? "E" : "W", absLon);
    }

    public FixedQuery posRadiusInKilometer(int km) {
        this.param("pos.radius", km + "km");
        return this;
    }

    public FixedQuery posRadiusInMeter(int m) {
        this.param("pos.radius", m + "m");
        return this;
    }

    public FixedQuery posRadiusInMile(int mi) {
        this.param("pos.radius", mi + "mi");
        return this;
    }

    public FixedQuery posBoundingBox(double n, double s, double e, double w) {
        this.param("pos.bb", Text.format("n=%f,s=%f,e=%f,w=%f", n, s, e, w));
        return this;
    }

    public FixedQuery streamingUserId(BigDecimal id) {
        this.param("streaming.userid", id);
        return this;
    }

    public FixedQuery streamingGroupName(String groupName) {
        this.param("streaming.groupname", groupName);
        return this;
    }

    public FixedQuery streamingSelection(String selection) {
        this.param("streaming.selection", selection);
        return this;
    }

    public FixedQuery streamingPriority(String priority) {
        this.param("streaming.priority", priority);
        return this;
    }

    public FixedQuery streamingMaxBucketsPerVisitor(int max) {
        this.param("streaming.maxbucketspervisitor", max);
        return this;
    }

    public FixedQuery rulesOff(boolean bool) {
        this.param("rules.off", bool);
        return this;
    }

    public FixedQuery rulesRulebase(String rulebase) {
        this.param("rules.rulebase", rulebase);
        return this;
    }

    public FixedQuery recall(String recall) {
        this.param("recall", recall);
        return this;
    }

    public FixedQuery user(String user) {
        this.param("user", user);
        return this;
    }

    public FixedQuery hitCountEstimate(boolean enable) {
        this.param("hitcountestimate", enable);
        return this;
    }

    public FixedQuery metricsIgnore(boolean bool) {
        this.param("metrics.ignore", bool);
        return this;
    }

    public FixedQuery param(String key, String value) {
        others.put(key, value);
        return this;
    }

    private FixedQuery param(String key, Object value) {
        this.param(key, value.toString());
        return this;
    }

    public FixedQuery params(Map<String, String> params) {
        others.putAll(params);
        return this;
    }

    /**
     * build the query map from the query
     *
     * @return the query map
     */
    public Map<String, String> buildQueryMap() {
        if (queryMap != null) {
            return queryMap;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("select ")
            .append(endQuery.queryChain.getSelect())
            .append(" from ")
            .append(endQuery.queryChain.getSources())
            .append(" where ")
            .append(endQuery.queryChain);

        if (!"".equals(endQuery.toString())) {
            sb.append(' ').append(endQuery);
        }

        queryMap = new LinkedHashMap<>(); // for the order
        queryMap.put("yql", sb.toString());
        queryMap.putAll(others);
        queryMap.putAll(getUserInputs());
        return queryMap;
    }

    /**
     * Builds the vespa query string joined by '&amp;'
     *
     * @return the query string
     */
    public String build() {
        return buildQueryMap().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    private Map<String, String> getUserInputs() {
        return getUserInputs(endQuery.queryChain.getQuery());
    }

    private Map<String, String> getUserInputs(Query q) {
        Map<String, String> param = new HashMap<>();
        q.queries.forEach(qu -> {
            if (qu instanceof Query) {
                param.putAll(getUserInputs((Query) qu));
            }
        });
        return param;
    }

    public boolean hasPositiveSearchField(String fieldName) {
        return endQuery.queryChain.hasPositiveSearchField(fieldName);
    }

    public boolean hasPositiveSearchField(String fieldName, Object value) {
        return endQuery.queryChain.hasPositiveSearchField(fieldName, value);
    }

    public boolean hasNegativeSearchField(String fieldName) {
        return endQuery.queryChain.hasNegativeSearchField(fieldName);
    }

    public boolean hasNegativeSearchField(String fieldName, Object value) {
        return endQuery.queryChain.hasNegativeSearchField(fieldName, value);
    }

}
