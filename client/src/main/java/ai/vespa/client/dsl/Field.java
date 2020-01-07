// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Field extends QueryChain {

    private String fieldName;
    private List<Object> values = new ArrayList<>();
    private Annotation annotation = A.empty();
    private String relation;

    Field(Sources sources, String fieldName) {
        this.sources = sources;
        this.fieldName = fieldName;
    }

    Field(Query query, String fieldName) {
        this.query = query;
        this.fieldName = fieldName;
    }

    public Query contains(String value) {
        return contains(A.empty(), value);
    }

    public Query contains(Annotation annotation, String value) {
        return common("contains", annotation, value);
    }

    public Query containsPhrase(String value, String... others) {
        return common("phrase", annotation, value, others);
    }

    public Query containsPhrase(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains phrase\" should not be empty");
        }

        return common("phrase", annotation, values);
    }

    public Query containsNear(String value, String... others) {
        return common("near", annotation, value, others);
    }

    public Query containsNear(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains near\" should not be empty");
        }

        return common("near", annotation, values);
    }

    public Query containsNear(Annotation annotation, String value, String... others) {
        return common("near", annotation, value, others);
    }

    public Query containsNear(Annotation annotation, List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains near\" should not be empty");
        }

        return common("near", annotation, values);
    }

    public Query containsOnear(String value, String... others) {
        return common("onear", annotation, value, others);
    }

    public Query containsOnear(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains onear\" should not be empty");
        }

        return common("onear", annotation, values);
    }

    public Query containsOnear(Annotation annotation, String value, String... others) {
        return common("onear", annotation, value, others);
    }

    public Query containsOnear(Annotation annotation, List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains onear\" should not be empty");
        }

        return common("onear", annotation, values);
    }

    public Query containsSameElement(Query andQuery) {
        return common("sameElement", annotation, andQuery);
    }

    public Query containsEquiv(String value, String... others) {
        return containsEquiv(Stream.concat(Stream.of(value), Stream.of(others)).collect(Collectors.toList()));
    }

    public Query containsEquiv(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains equiv\" should not be empty");
        } else if (values.size() == 1) {
            // Vespa does not support one element equiv syntax, use contains instead
            return contains(values.get(0));
        } else {
            return common("equiv", annotation, values);
        }
    }

    public Query containsUri(String value) {
        return common("uri", annotation, value) ;
    }

    public Query containsUri(Annotation annotation, String value) {
        return common("uri", annotation, value) ;
    }

    public Query matches(String str) {
        return common("matches", annotation, str);
    }

    public Query eq(int t) {
        return common("=", annotation, t);
    }

    public Query ge(int t) {
        return common(">=", annotation, t);
    }

    public Query gt(int t) {
        return common(">", annotation, t);
    }

    public Query le(int t) {
        return common("<=", annotation, t);
    }

    public Query lt(int t) {
        return common("<", annotation, t);
    }

    public Query inRange(int l, int m) {
        return common("range", annotation, l, new Integer[]{m});
    }

    public Query eq(long t) {
        return common("=", annotation, t);
    }

    public Query ge(long t) {
        return common(">=", annotation, t);
    }

    public Query gt(long t) {
        return common(">", annotation, t);
    }

    public Query le(long t) {
        return common("<=", annotation, t);
    }

    public Query lt(long t) {
        return common("<", annotation, t);
    }

    public Query inRange(long l, long m) {
        return common("range", annotation, l, new Long[]{m});
    }


    public Query isTrue() {
        return common("=", annotation, true);
    }

    public Query isFalse() {
        return common("=", annotation, false);
    }

    private Query common(String relation, Annotation annotation, Object value) {
        return common(relation, annotation, value, values.toArray());
    }

    private Query common(String relation, Annotation annotation, String value) {
        Object v = "\"" + StringEscapeUtils.escapeJava(value) + "\"";
        return common(relation, annotation, v, values.toArray());
    }

    private Query common(String relation, Annotation annotation, List<String> values) {
        return common(relation, annotation, values.get(0), values.subList(1, values.size()).toArray(new String[0]));
    }

    private Query common(String relation, Annotation annotation, String value, String[] others) {
        Object v = "\"" + StringEscapeUtils.escapeJava(value) + "\"";
        Object[] o = Stream.of(others).map(s -> "\"" + StringEscapeUtils.escapeJava(s) + "\"").toArray();
        return common(relation, annotation, v, o);
    }

    private Query common(String relation, Annotation annotation, Object value, Object[] others) {
        this.annotation = annotation;
        this.relation = relation;
        this.values = Stream.concat(Stream.of(value), Stream.of(others)).collect(Collectors.toList());
        this.nonEmpty = true;
        return query != null
               ? query
               : new Query(sources, this);
    }

    @Override
    public String toString() {
        boolean hasAnnotation = !A.empty().equals(annotation);
        String valuesStr;
        switch (relation) {
            case "range":
                valuesStr = values.stream()
                    .map(i -> i instanceof Long ? i.toString() + "L" : i.toString())
                    .collect(Collectors.joining(", "));

                return hasAnnotation
                       ? String.format("([%s]range(%s, %s))", annotation, fieldName, valuesStr)
                       : String.format("range(%s, %s)", fieldName, valuesStr);
            case "near":
            case "onear":
            case "phrase":
            case "equiv":
            case "uri":
                valuesStr = values.stream().map(Object::toString).collect(Collectors.joining(", "));
                return hasAnnotation
                       ? String.format("%s contains ([%s]%s(%s))", fieldName, annotation, relation, valuesStr)
                       : String.format("%s contains %s(%s)", fieldName, relation, valuesStr);
            case "sameElement":
                return String.format("%s contains %s(%s)", fieldName, relation,
                                     ((Query) values.get(0)).toCommaSeparatedAndQueries());
            default:
                Object value = values.get(0);
                valuesStr = value instanceof Long ? value.toString() + "L" : value.toString();
                return hasAnnotation
                       ? String.format("%s %s ([%s]%s)", fieldName, relation, annotation, valuesStr)
                       : String.format("%s %s %s", fieldName, relation, valuesStr);
        }
    }

    @Override
    boolean hasPositiveSearchField(String fieldName) {
        return !"andnot".equals(this.op) && this.fieldName.equals(fieldName);
    }

    @Override
    boolean hasPositiveSearchField(String fieldName, Object value) {
        return hasPositiveSearchField(fieldName) && valuesContains(value);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName) {
        return "andnot".equals(this.op) && this.fieldName.equals(fieldName);
    }

    @Override
    boolean hasNegativeSearchField(String fieldName, Object value) {
        return hasNegativeSearchField(fieldName) && valuesContains(value);
    }

    boolean valuesContains(Object value) {
        if (value instanceof String) {
            value = "\"" + value + "\"";
        }
        return values.contains(value);
    }

}
