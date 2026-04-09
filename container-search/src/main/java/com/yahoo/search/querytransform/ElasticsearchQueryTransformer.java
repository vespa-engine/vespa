// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.Limit;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.RangeItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ElasticsearchQueryTransformer extends Searcher {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String ES_QUERY_PROPERTY = "elasticsearch.query";

    @Override
    public Result search(Query query, Execution execution) {
        String esQuery = query.properties().getString(ES_QUERY_PROPERTY);
        if (esQuery != null) {
            try {
                JsonNode root = mapper.readTree(esQuery);
                Item item = translateQuery(root.get("query"));
                query.getModel().getQueryTree().setRoot(item);
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new IllegalInputException("Failed parsing 'elasticsearch.query'", e);
            }
        }
        return execution.search(query);
    }

    private Item translateQuery(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Missing Elasticsearch 'query' field");
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Elasticsearch 'query' must be an object");
        }

        if (node.has("match")) {
            JsonNode match = node.get("match");
            return translateWordClause(match, "match");
        }
        if (node.has("term")) {
            JsonNode term = node.get("term");
            return translateWordClause(term, "term");
        }
        if (node.has("range")) {
            return translateRangeClause(node.get("range"));
        }
        if (node.has("bool")) {
            JsonNode bool = node.get("bool");
            if (!bool.isObject()) {
                throw new IllegalArgumentException("Elasticsearch 'bool' clause must be an object");
            }

            AndItem and = new AndItem();
            OrItem or = new OrItem();
            NotItem not = new NotItem();

            if (bool.has("must")) {
                for (JsonNode clause : clauses(bool.get("must"), "must")) {
                    and.addItem(translateQuery(clause));
                }
                return and;
            }
            if (bool.has("should")) {
                for (JsonNode clause : clauses(bool.get("should"), "should")) {
                    or.addItem(translateQuery(clause));
                }
                return or;
            }
            if (bool.has("must_not")) {
                for (JsonNode clause : clauses(bool.get("must_not"), "must_not")) {
                    not.addNegativeItem(translateQuery(clause));
                }
                return not;
            }
            throw new IllegalArgumentException("Elasticsearch 'bool' must contain one of 'must', 'should', or 'must_not'");
        }

        throw new IllegalArgumentException("Unsupported Elasticsearch query type");
    }

    private WordItem translateWordClause(JsonNode node, String type) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Elasticsearch '" + type + "' clause must be an object");
        }

        Iterator<String> fields = node.fieldNames();
        if (!fields.hasNext()) {
            throw new IllegalArgumentException("Elasticsearch '" + type + "' clause must contain one field");
        }

        String field = fields.next();
        JsonNode valueNode = node.get(field);
        if (valueNode == null || valueNode.isNull()) {
            throw new IllegalArgumentException("Elasticsearch '" + type + "' value is missing");
        }

        if (valueNode.isObject()) {
            JsonNode nestedValue = valueNode.get("value");
            if (nestedValue == null || nestedValue.isNull()) {
                nestedValue = valueNode.get("query");
            }
            if (nestedValue == null || nestedValue.isNull()) {
                throw new IllegalArgumentException("Elasticsearch '" + type + "' object must contain 'value' or 'query'");
            }
            valueNode = nestedValue;
        }

        return new WordItem(valueNode.asText(), field);
    }

    private RangeItem translateRangeClause(JsonNode node) {
        if (!node.isObject()) {
            throw new IllegalArgumentException("Elasticsearch 'range' clause must be an object");
        }

        Iterator<String> fields = node.fieldNames();
        if (!fields.hasNext()) {
            throw new IllegalArgumentException("Elasticsearch 'range' clause must contain one field");
        }

        String field = fields.next();
        JsonNode bounds = node.get(field);
        if (bounds == null || !bounds.isObject()) {
            throw new IllegalArgumentException("Elasticsearch 'range' bounds must be an object");
        }

        if (bounds.has("gte") && bounds.has("gt")) {
            throw new IllegalArgumentException("Elasticsearch 'range' cannot contain both 'gte' and 'gt'");
        }
        if (bounds.has("lte") && bounds.has("lt")) {
            throw new IllegalArgumentException("Elasticsearch 'range' cannot contain both 'lte' and 'lt'");
        }

        Limit from = Limit.NEGATIVE_INFINITY;
        Limit to = Limit.POSITIVE_INFINITY;
        boolean hasBounds = false;

        if (bounds.has("gte")) {
            from = new Limit(numberValue(bounds.get("gte"), "gte"), true);
            hasBounds = true;
        }
        if (bounds.has("gt")) {
            from = new Limit(numberValue(bounds.get("gt"), "gt"), false);
            hasBounds = true;
        }
        if (bounds.has("lte")) {
            to = new Limit(numberValue(bounds.get("lte"), "lte"), true);
            hasBounds = true;
        }
        if (bounds.has("lt")) {
            to = new Limit(numberValue(bounds.get("lt"), "lt"), false);
            hasBounds = true;
        }

        if (!hasBounds) {
            throw new IllegalArgumentException("Elasticsearch 'range' must include one of gte, gt, lte, or lt");
        }

        return new RangeItem(from, to, field);
    }

    private Number numberValue(JsonNode node, String operator) {
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException("Elasticsearch range '" + operator + "' must be numeric");
        }
        return node.numberValue();
    }

    private List<JsonNode> clauses(JsonNode node, String clauseName) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Elasticsearch bool '" + clauseName + "' clause is missing");
        }

        if (node.isArray()) {
            List<JsonNode> clauses = new ArrayList<>();
            for (JsonNode clause : node) {
                clauses.add(clause);
            }
            if (clauses.isEmpty()) {
                throw new IllegalArgumentException("Elasticsearch bool '" + clauseName + "' clause cannot be empty");
            }
            return clauses;
        }

        if (node.isObject()) {
            return List.of(node);
        }

        throw new IllegalArgumentException("Elasticsearch bool '" + clauseName + "' clause must be an object or array");
    }
}
