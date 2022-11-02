// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * EndQuery contains a 'Query'
 * This object holds timeout, offset, limit, group and orderBy information before the semicolon
 */
public class EndQuery {

    final QueryChain queryChain;
    final Map<String, Integer> map = new LinkedHashMap<>();
    final List<Object[]> order = new ArrayList<>();
    private String groupQueryStr;

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

    /**
     * Offset.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#limit-offset
     *
     * @param offset the offset
     * @return the end query
     */
    public EndQuery offset(int offset) {
        return this.setOffset(offset);
    }

    /**
     * Timeout.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#timeout
     *
     * @param timeout the timeout
     * @return the end query
     */
    public EndQuery timeout(int timeout) {
        return this.setTimeout(timeout);
    }

    /**
     * Limit.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#limit-offset
     *
     * @param limit the limit
     * @return the end query
     */
    public EndQuery limit(int limit) {
        return this.setLimit(limit);
    }

    /**
     * Calls fix()
     *
     * @deprecated use {link #fix}
     */
    @Deprecated // TODO: Remove on Vespa 9
    public FixedQuery semicolon() { return fix(); }

    /** Returns a fixed query containing this. */
    public FixedQuery fix() {
        return new FixedQuery(this);
    }

    /** Calls fix().build() */
    public String build() { return fix().build(); }

    /**
     * Group.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#grouping
     *
     * @param group the group
     * @return the end query
     */
    public EndQuery group(Group group) {
        return group(group.toString());
    }

    /**
     * Group.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#grouping
     *
     * @param groupQueryStr the group str
     * @return the end query
     */
    public EndQuery group(String groupQueryStr) {
        this.groupQueryStr = groupQueryStr;
        return this;
    }

    /**
     * Order by asc.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#order-by
     *
     * @param annotation the annotation
     * @param fieldName the field name
     * @return the end query
     */
    public EndQuery orderByAsc(Annotation annotation, String fieldName) {
        order.add(new Object[]{annotation, fieldName, "asc"});
        return this;
    }

    /**
     * Order by asc.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#order-by
     *
     * @param fieldName the field name
     * @return the end query
     */
    public EndQuery orderByAsc(String fieldName) {
        order.add(new Object[]{A.empty(), fieldName, "asc"});
        return this;
    }

    /**
     * Order by desc.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#order-by
     *
     * @param annotation the annotation
     * @param fieldName the field name
     * @return the end query
     */
    public EndQuery orderByDesc(Annotation annotation, String fieldName) {
        order.add(new Object[]{annotation, fieldName, "desc"});
        return this;
    }

    /**
     * Order by desc.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#order-by
     *
     * @param fieldName the field name
     * @return the end query
     */
    public EndQuery orderByDesc(String fieldName) {
        order.add(new Object[]{A.empty(), fieldName, "desc"});
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String orderStr = order.stream().map(array -> A.empty().equals(array[0])
                                                      ? Text.format("%s %s", array[1], array[2])
                                                      : Text.format("%s%s %s", array[0], array[1], array[2]))
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
            sb.append("order by ").append(orderStr).append(" ").append(others);
        }

        if (groupQueryStr != null) {
            sb.append("| ").append(groupQueryStr);
        }

        return sb.toString();
    }

}