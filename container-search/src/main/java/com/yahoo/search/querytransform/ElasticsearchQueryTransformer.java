package com.yahoo.search.querytransform;

import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ElasticsearchQueryTransformer extends Searcher {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Result search(Query query, Execution execution) {
        String esQuery = query.properties().getString("elasticsearch.query");
        if (esQuery != null) {
            try {
                JsonNode root = mapper.readTree(esQuery);
                Item item = translateQuery(root.get("query"));
                if (item != null) {
                    query.getModel().getQueryTree().setRoot(item);
                }
            } catch (Exception e) {
                // log and continue
            }
        }
        return execution.search(query);
    }

    private Item translateQuery(JsonNode node) {
        if (node == null) return null;

        if (node.has("match")) {
            JsonNode match = node.get("match");
            String field = match.fieldNames().next();
            return new WordItem(match.get(field).asText(), field);
        }
        if (node.has("term")) {
            JsonNode term = node.get("term");
            String field = term.fieldNames().next();
            return new WordItem(term.get(field).asText(), field);
        }
        if (node.has("range")) {
            JsonNode range = node.get("range");
            String field = range.fieldNames().next();
            JsonNode bounds = range.get(field);
            RangeItem item = new RangeItem(field);
            if (bounds.has("gte")) item.setFromInclusive(bounds.get("gte").asDouble());
            if (bounds.has("gt"))  item.setFromExclusive(bounds.get("gt").asDouble());
            if (bounds.has("lte")) item.setToInclusive(bounds.get("lte").asDouble());
            if (bounds.has("lt"))  item.setToExclusive(bounds.get("lt").asDouble());
            return item;
        }
        if (node.has("bool")) {
            JsonNode bool = node.get("bool");
            AndItem and = new AndItem();
            OrItem or = new OrItem();
            NotItem not = new NotItem();

            if (bool.has("must")) {
                for (JsonNode clause : bool.get("must"))
                    and.addItem(translateQuery(clause));
                return and;
            }
            if (bool.has("should")) {
                for (JsonNode clause : bool.get("should"))
                    or.addItem(translateQuery(clause));
                return or;
            }
            if (bool.has("must_not")) {
                for (JsonNode clause : bool.get("must_not"))
                    not.addNegativeItem(translateQuery(clause));
                return not;
            }
        }
        return null;
    }
}