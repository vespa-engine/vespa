// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.client.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EndQuery {

    QueryChain queryChain;
    Map<String, Integer> map = new LinkedHashMap<>();
    List<Object[]> order = new ArrayList<>();
    String groupQueryStr;

    EndQuery(QueryChain queryChain) {
        this.queryChain = queryChain;

        // make sure the order of limit, offset and timeout
        this.map.put("limit", null);
        this.map.put("offset", null);
        this.map.put("timeout", null);
    }

    EndQuery setTimeout(Integer timeout) {
        map.put("timeout", timeout);
        return this;
    }

    EndQuery setOffset(int offset) {
        map.put("offset", offset);
        return this;
    }

    EndQuery setLimit(int limit) {
        map.put("limit", limit);
        return this;
    }

    public EndQuery offset(int offset) {
        return this.setOffset(offset);
    }

    public EndQuery timeout(int timeout) {
        return this.setTimeout(timeout);
    }

    public EndQuery limit(int limit) {
        return this.setLimit(limit);
    }

    public FixedQuery semicolon() {
        return new FixedQuery(this);
    }

    public EndQuery group(Group group) {
        this.groupQueryStr = group.toString();
        return this;
    }

    public EndQuery group(String groupQueryStr) {
        this.groupQueryStr = groupQueryStr;
        return this;
    }

    public EndQuery orderByAsc(Annotation annotation, String fieldName) {
        order.add(new Object[]{annotation, fieldName, "asc"});
        return this;
    }

    public EndQuery orderByAsc(String fieldName) {
        order.add(new Object[]{A.empty(), fieldName, "asc"});
        return this;
    }

    public EndQuery orderByDesc(Annotation annotation, String fieldName) {
        order.add(new Object[]{annotation, fieldName, "desc"});
        return this;
    }

    public EndQuery orderByDesc(String fieldName) {
        order.add(new Object[]{A.empty(), fieldName, "desc"});
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String orderStr = order.stream().map(array -> A.empty().equals(array[0])
                                                      ? String.format("%s %s", array[1], array[2])
                                                      : String.format("[%s]%s %s", array[0], array[1], array[2]))
            .collect(Collectors.joining(", "));

        String others = map.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> entry.getKey() + " " + entry.getValue())
            .collect(Collectors.joining(" "));

        if (orderStr.isEmpty()) {
            sb.append(others.isEmpty() ? "" : others);
        } else if (others.isEmpty()) {
            sb.append("order by ").append(orderStr);
        } else {
            sb.append("order by ").append(orderStr).append(", ").append(others);
        }

        if (groupQueryStr != null) {
            sb.append("| ").append(groupQueryStr);
        }

        return sb.toString();
    }
}
