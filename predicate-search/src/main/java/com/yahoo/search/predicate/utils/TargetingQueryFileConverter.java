// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.utils;

import com.google.common.net.UrlEscapers;
import com.yahoo.search.predicate.PredicateQuery;
import com.yahoo.search.predicate.serialization.PredicateQuerySerializer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * Converts a targeting query (the format provided by targeting team) into a file of Vespa queries formatted as URLs.
 *
 * The format is the following:
 * - Each line represents one bulk query (upto 64 subqueries)
 * - Each bulk query has a set of subqueries separated by ";"
 * - Each subquery is of the format: attrName\tattrValue\tsubqueryIndex\tisRangeTerm;
 * - Some attributes have no value.
 * - Value may contain ";"
 *
 * @author bjorncs
 */
public class TargetingQueryFileConverter {

    // Subqueries having more than this value are skipped.
    private static final int MAX_NUMBER_OF_TERMS = 100;

    private enum OutputFormat {JSON, YQL}

    private TargetingQueryFileConverter() {}

    public static void main(String[] args) throws IOException {
        int nQueries = 123042;
        int batchFactor = 64;
        Subqueries subqueries = parseRiseQueries(new File("test-data/rise-query2.txt"), nQueries);
        filterOutHugeSubqueries(subqueries);
        List<Query> queries = batchSubqueries(subqueries, batchFactor);
        writeSubqueriesToFile(
                queries,
                new File("test-data/targeting-queries-json-" + batchFactor + "b-" + nQueries + "n.txt"),
                OutputFormat.JSON);
        writeSubqueriesToFile(
                queries,
                new File("test-data/targeting-queries-yql-" + batchFactor + "b-" + nQueries + "n.txt"),
                OutputFormat.YQL);
    }


    private static void writeSubqueriesToFile(List<Query> queries, File output, OutputFormat outputFormat)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
            if (outputFormat == OutputFormat.JSON) {
                writeJSONOutput(writer, queries);
            } else {
                writeYQLOutput(writer, queries);
            }

        }
    }

    private static void writeJSONOutput(BufferedWriter writer, List<Query> queries) throws IOException {
        PredicateQuerySerializer serializer = new PredicateQuerySerializer();
        for (Query query : queries) {
            PredicateQuery predicateQuery = toPredicateQuery(query);
            String json = serializer.toJSON(predicateQuery);
            writer.append(json).append('\n');
        }
    }

    private static PredicateQuery toPredicateQuery(Query query) {
        PredicateQuery predicateQuery = new PredicateQuery();
        for (Map.Entry<Long, Set<Feature>> e : query.valuesForSubqueries.entrySet()) {
            e.getValue().forEach(f -> predicateQuery.addFeature(f.key, f.strValue, e.getKey()));
        }
        for (Map.Entry<Long, Set<Feature>> e : query.rangesForSubqueries.entrySet()) {
            e.getValue().forEach(f -> predicateQuery.addRangeFeature(f.key, f.longValue, e.getKey()));
        }
        return predicateQuery;
    }

    private static void writeYQLOutput(BufferedWriter writer, List<Query> queries) throws IOException {
        for (Query query : queries) {
            writer.append(toYqlString(query)).append('\n');
        }
    }

    private static String toYqlString(Query query)  {
        StringBuilder yqlBuilder = new StringBuilder("select * from sources * where predicate(boolean, ");
        yqlBuilder
                .append(createYqlFormatSubqueryMapString(query.valuesForSubqueries, query.isSingleQuery))
                .append(", ")
                .append(createYqlFormatSubqueryMapString(query.rangesForSubqueries, query.isSingleQuery))
                .append(");");
        return "/search/?query&nocache&yql=" + UrlEscapers.urlFormParameterEscaper().escape(yqlBuilder.toString());
    }

    /*
     * The subqueryBatchFactor determines the batch factor for each query. A maximum of 64 queries can be batched
     * into a single query (as subqueries).
     *      0 => Do not batch and output plain queries (no subquery).
     *      1 => Do not batch, but output queries with single subquery.
     */
    private static List<Query> batchSubqueries(Subqueries subqueries, int subqueryBatchFactor) {
        Iterator<Integer> iterator = subqueries.subqueries.iterator();
        List<Query> result = new ArrayList<>();
        while (iterator.hasNext()) {
            // Aggregate the subqueries that contains a given value.
            Map<Feature, Long> subqueriesForValue = new TreeMap<>();
            Map<Feature, Long> subqueriesForRange = new TreeMap<>();
            // Batch single to single subquery for batch factor 0.
            for (int i = 0; i < Math.max(1, subqueryBatchFactor) && iterator.hasNext(); ++i) {
                Integer subquery = iterator.next();
                registerSubqueryValues(i, subqueries.valuesForSubquery.get(subquery), subqueriesForValue);
                registerSubqueryValues(i, subqueries.rangesForSubquery.get(subquery), subqueriesForRange);
            }

            // Aggregate the values that are contained in a given set of subqueries.
            Query query = new Query(subqueryBatchFactor == 0);
            simplifyAndFillQueryValues(query.valuesForSubqueries, subqueriesForValue);
            simplifyAndFillQueryValues(query.rangesForSubqueries, subqueriesForRange);
            result.add(query);
        }
        return result;
    }

    private static void registerSubqueryValues(int subquery, Set<Feature> values, Map<Feature, Long> subqueriesForValue) {
        if (values != null) {
            values.forEach(value -> subqueriesForValue.merge(value, 1L << subquery, (ids1, ids2) -> ids1 | ids2));
        }
    }

    private static void simplifyAndFillQueryValues(Map<Long, Set<Feature>> queryValues, Map<Feature, Long> subqueriesForValue) {
        for (Map.Entry<Feature, Long> entry : subqueriesForValue.entrySet()) {
            Feature feature = entry.getKey();
            Long subqueryBitmap = entry.getValue();
            Set<Feature> featureSet = queryValues.computeIfAbsent(subqueryBitmap, (k) -> new HashSet<>());
            featureSet.add(feature);
        }
    }

    private static String createYqlFormatSubqueryMapString(Map<Long, Set<Feature>> subqueriesForString, boolean isSingleQuery) {
        return subqueriesForString.entrySet().stream()
                .map(e -> {
                    Stream<String> features = e.getValue().stream().map(Feature::asYqlString);
                    if (isSingleQuery) {
                        return features.collect(joining(", "));
                    } else {
                        // Note: Cannot use method reference as both method toString(int) and method toString() match.
                        String values = features.collect(joining(", ", "{", "}"));
                        return String.format("\"0x%s\":%s", Long.toHexString(e.getKey()), values);
                    }
                })
                .collect(joining(", ", "{", "}"));
    }

    private static Subqueries parseRiseQueries(File riseQueryFile, int maxQueries) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(riseQueryFile))) {
            Subqueries parsedSubqueries = new Subqueries();
            AtomicInteger counter = new AtomicInteger(1);
            reader.lines()
                    .limit(maxQueries)
                    .forEach(riseQuery -> parseRiseQuery(parsedSubqueries, riseQuery, counter.getAndIncrement()));
            return parsedSubqueries;
        }
    }

    private static void filterOutHugeSubqueries(Subqueries subqueries) {
        Iterator<Integer> iterator = subqueries.subqueries.iterator();
        while (iterator.hasNext()) {
            Integer subquery = iterator.next();
            Set<Feature> values = subqueries.valuesForSubquery.get(subquery);
            Set<Feature> ranges = subqueries.rangesForSubquery.get(subquery);
            int sizeValues = values == null ? 0 : values.size();
            int sizeRanges = ranges == null ? 0 : ranges.size();
            if (sizeValues + sizeRanges > MAX_NUMBER_OF_TERMS) {
                iterator.remove();
                subqueries.valuesForSubquery.remove(subquery);
                subqueries.rangesForSubquery.remove(subquery);
            }
        }
    }

    private static void parseRiseQuery(Subqueries subqueries, String queryString, int queryId) {
        StringTokenizer subQueryTokenizer = new StringTokenizer(queryString, "\t", true);
        while (subQueryTokenizer.hasMoreTokens()) {
            String key = subQueryTokenizer.nextToken("\t");
            subQueryTokenizer.nextToken();  // Consume delimiter
            String value = subQueryTokenizer.nextToken();
            if (value.equals("\t")) {
                value = "";
            } else {
                subQueryTokenizer.nextToken();  // Consume delimiter
            }
            int subQueryIndex = Integer.parseInt(subQueryTokenizer.nextToken());
            subQueryTokenizer.nextToken();  // Consume delimiter
            boolean isRangeTerm = Boolean.parseBoolean(subQueryTokenizer.nextToken(";"));
            if (subQueryTokenizer.hasMoreTokens()) {
                subQueryTokenizer.nextToken();  // Consume delimiter
            }
            int subqueryId = subQueryIndex + 64 * queryId;
            if (isRangeTerm) {
                Set<Feature> rangeFeatures = subqueries.rangesForSubquery.computeIfAbsent(
                        subqueryId, (id) -> new HashSet<>());
                rangeFeatures.add(new Feature(key, Long.parseLong(value)));
            } else {
                Set<Feature> features = subqueries.valuesForSubquery.computeIfAbsent(subqueryId, (id) -> new HashSet<>());
                features.add(new Feature(key, value));
            }
            subqueries.subqueries.add(subqueryId);
        }
    }

    private static class Subqueries {
        public final TreeSet<Integer> subqueries = new TreeSet<>();
        public final Map<Integer, Set<Feature>> valuesForSubquery = new HashMap<>();
        public final Map<Integer, Set<Feature>> rangesForSubquery = new HashMap<>();
    }

    private static class Query {
        public final boolean isSingleQuery;
        public final Map<Long, Set<Feature>> valuesForSubqueries = new TreeMap<>();
        public final Map<Long, Set<Feature>> rangesForSubqueries = new TreeMap<>();

        public Query(boolean isSingleQuery) {
            this.isSingleQuery = isSingleQuery;
        }
    }

    private static class Feature implements Comparable<Feature> {
        public final String key;
        private final String strValue;
        private final long longValue;

        public Feature(String key, String value) {
            this.key = key;
            this.strValue = value;
            this.longValue = 0;
        }

        public Feature(String key, long value) {
            this.key = key;
            this.strValue = null;
            this.longValue = value;
        }

        public String asYqlString() {
            if (strValue != null) {
                return String.format("\"%s\":\"%s\"", key, strValue);
            } else {
                return String.format("\"%s\":%dl", key, longValue);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Feature)) return false;

            Feature feature = (Feature) o;

            if (longValue != feature.longValue) return false;
            if (!key.equals(feature.key)) return false;
            return !(strValue != null ? !strValue.equals(feature.strValue) : feature.strValue != null);

        }

        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + (strValue != null ? strValue.hashCode() : 0);
            result = 31 * result + (int) (longValue ^ (longValue >>> 32));
            return result;
        }

        @Override
        public int compareTo(Feature o) {
            return asYqlString().compareTo(o.asYqlString());
        }
    }

}
