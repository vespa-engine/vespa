// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Field extends QueryChain {

    private final String fieldName;
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

    /**
     * Contains query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value the value
     * @return the query
     */
    public Query contains(String value) {
        return contains(A.empty(), value);
    }

    /**
     * Contains query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param value      the value
     * @return the query
     */
    public Query contains(Annotation annotation, String value) {
        return common("contains", annotation, value);
    }

    /**
     * Contains phrase query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value  the value
     * @param others the others
     * @return the query
     */
    public Query containsPhrase(String value, String... others) {
        return common("phrase", annotation, value, others);
    }

    /**
     * Contains phrase query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param values the values
     * @return the query
     */
    public Query containsPhrase(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains phrase\" should not be empty");
        }

        return common("phrase", annotation, values);
    }

    /**
     * Contains near query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value  the value
     * @param others the others
     * @return the query
     */
    public Query containsNear(String value, String... others) {
        return common("near", annotation, value, others);
    }

    /**
     * Contains near query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param values the values
     * @return the query
     */
    public Query containsNear(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains near\" should not be empty");
        }

        return common("near", annotation, values);
    }

    /**
     * Contains near query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param value      the value
     * @param others     the others
     * @return the query
     */
    public Query containsNear(Annotation annotation, String value, String... others) {
        return common("near", annotation, value, others);
    }

    /**
     * Contains near query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param values     the values
     * @return the query
     */
    public Query containsNear(Annotation annotation, List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains near\" should not be empty");
        }

        return common("near", annotation, values);
    }

    /**
     * Contains onear query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value  the value
     * @param others the others
     * @return the query
     */
    public Query containsOnear(String value, String... others) {
        return common("onear", annotation, value, others);
    }

    /**
     * Contains onear query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param values the values
     * @return the query
     */
    public Query containsOnear(List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains onear\" should not be empty");
        }

        return common("onear", annotation, values);
    }

    /**
     * Contains onear query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param value      the value
     * @param others     the others
     * @return the query
     */
    public Query containsOnear(Annotation annotation, String value, String... others) {
        return common("onear", annotation, value, others);
    }

    /**
     * Contains onear query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param values     the values
     * @return the query
     */
    public Query containsOnear(Annotation annotation, List<String> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("value of \"contains onear\" should not be empty");
        }

        return common("onear", annotation, values);
    }

    /**
     * Contains same element query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param andQuery the and query
     * @return the query
     */
    public Query containsSameElement(Query andQuery) {
        return common("sameElement", annotation, andQuery);
    }

    /**
     * Contains equiv query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value  the value
     * @param others the others
     * @return the query
     */
    public Query containsEquiv(String value, String... others) {
        return containsEquiv(Stream.concat(Stream.of(value), Stream.of(others)).collect(Collectors.toList()));
    }

    /**
     * Contains equiv query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param values the values
     * @return the query
     */
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

    /**
     * Contains uri query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param value the value
     * @return the query
     */
    public Query containsUri(String value) {
        return common("uri", annotation, value) ;
    }

    /**
     * Contains uri query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#contains
     *
     * @param annotation the annotation
     * @param value      the value
     * @return the query
     */
    public Query containsUri(Annotation annotation, String value) {
        return common("uri", annotation, value) ;
    }

    /**
     * Matches query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#matches
     *
     * @param str the str
     * @return the query
     */
    public Query matches(String str) {
        return common("matches", annotation, str);
    }

    /**
     * Equals query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query eq(int t) {
        return common("=", annotation, t);
    }

    /**
     * Greater than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query ge(int t) {
        return common(">=", annotation, t);
    }

    /**
     * Greater than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query gt(int t) {
        return common(">", annotation, t);
    }

    /**
     * Less than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query le(int t) {
        return common("<=", annotation, t);
    }

    /**
     * Less than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query lt(int t) {
        return common("<", annotation, t);
    }

    /**
     * In range query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param l the l
     * @param m the m
     * @return the query
     */
    public Query inRange(int l, int m) {
        return common("range", annotation, l, new Integer[]{m});
    }

    /**
     * Equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query eq(long t) {
        return common("=", annotation, t);
    }

    /**
     * Greater than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query ge(long t) {
        return common(">=", annotation, t);
    }

    /**
     * Greater than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query gt(long t) {
        return common(">", annotation, t);
    }

    /**
     * Less than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query le(long t) {
        return common("<=", annotation, t);
    }

    /**
     * Less than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query lt(long t) {
        return common("<", annotation, t);
    }

    /**
     * In range query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param l the l
     * @param m the m
     * @return the query
     */
    public Query inRange(long l, long m) {
        return common("range", annotation, l, new Long[]{m});
    }

    /**
     * Equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query eq(double t) {
        return common("=", annotation, t);
    }

    /**
     * Greater than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query ge(double t) {
        return common(">=", annotation, t);
    }

    /**
     * Greater than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query gt(double t) {
        return common(">", annotation, t);
    }

    /**
     * Less than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query le(double t) {
        return common("<=", annotation, t);
    }

    /**
     * Less than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query lt(double t) {
        return common("<", annotation, t);
    }

    /**
     * In range query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param l the l
     * @param m the m
     * @return the query
     */
    public Query inRange(double l, double m) {
        return common("range", annotation, l, new Double[]{m});
    }

    /**
     * Equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query eq(float t) {
        return common("=", annotation, t);
    }

    /**
     * Greater than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query ge(float t) {
        return common(">=", annotation, t);
    }

    /**
     * Greater than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query gt(float t) {
        return common(">", annotation, t);
    }

    /**
     * Less than or equal to query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query le(float t) {
        return common("<=", annotation, t);
    }

    /**
     * Less than query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param t the t
     * @return the query
     */
    public Query lt(float t) {
        return common("<", annotation, t);
    }

    /**
     * In range query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#numeric
     *
     * @param l the l
     * @param m the m
     * @return the query
     */
    public Query inRange(float l, float m) {
        return common("range", annotation, l, new Float[]{m});
    }

    /**
     * Is true query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#boolean
     *
     * @return the query
     */
    public Query isTrue() {
        return common("=", annotation, true);
    }

    /**
     * Is false query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#boolean
     *
     * @return the query
     */
    public Query isFalse() {
        return common("=", annotation, false);
    }

    /**
     * Nearest neighbor query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor
     *
     * @param rankFeature the rankfeature.
     * @return the query
     */
    public Query nearestNeighbor(String rankFeature) {
        return common("nearestNeighbor", annotation, (Object) rankFeature);
    }

    /**
     * Nearest neighbor query.
     * https://docs.vespa.ai/en/reference/query-language-reference.html#nearestneighbor
     *
     * @param annotation the annotation
     * @param rankFeature the rankfeature.
     * @return the query
     */
    public Query nearestNeighbor(Annotation annotation, String rankFeature) {
        return common("nearestNeighbor", annotation, (Object) rankFeature);
    }

    private Query common(String relation, Annotation annotation, Object value) {
        return common(relation, annotation, value, values.toArray());
    }

    private Query common(String relation, Annotation annotation, String value) {
        Object v = Q.toJson(value);
        return common(relation, annotation, v, values.toArray());
    }

    private Query common(String relation, Annotation annotation, List<String> values) {
        return common(relation, annotation, values.get(0), values.subList(1, values.size()).toArray(new String[0]));
    }

    private Query common(String relation, Annotation annotation, String value, String[] others) {
        Object v = Q.toJson(value);
        Object[] o = Stream.of(others).map(Q::toJson).toArray();
        return common(relation, annotation, v, o);
    }

    private Query common(String relation, Annotation annotation, Object value, Object[] others) {
        this.annotation = annotation;
        this.relation = relation;
        this.values = Stream.concat(Stream.of(value), Stream.of(others)).collect(Collectors.toList());
        this.nonEmpty = true;
        return query != null ? query : new Query(sources, this);
    }

    @Override
    public String toString() {
        boolean hasAnnotation = !A.empty().equals(annotation);
        String valuesStr;
        switch (relation) {
            case "range":
                valuesStr = values.stream()
                    .map(i -> i instanceof Long ? i + "L" : i.toString())
                    .collect(Collectors.joining(", "));

                return hasAnnotation
                       ? Text.format("([%s]range(%s, %s))", annotation, fieldName, valuesStr)
                       : Text.format("range(%s, %s)", fieldName, valuesStr);
            case "near":
            case "onear":
            case "phrase":
            case "equiv":
            case "uri":
                valuesStr = values.stream().map(Object::toString).collect(Collectors.joining(", "));
                return hasAnnotation
                       ? Text.format("%s contains ([%s]%s(%s))", fieldName, annotation, relation, valuesStr)
                       : Text.format("%s contains %s(%s)", fieldName, relation, valuesStr);
            case "sameElement":
                return Text.format("%s contains %s(%s)", fieldName, relation,
                                     ((Query) values.get(0)).toCommaSeparatedAndQueries());
            case "nearestNeighbor":
                valuesStr = values.stream().map(i -> (String) i).collect(Collectors.joining(", "));

                return hasAnnotation
                    ? Text.format("([%s]nearestNeighbor(%s, %s))", annotation, fieldName, valuesStr)
                    : Text.format("nearestNeighbor(%s, %s)", fieldName, valuesStr);
            default:
                Object value = values.get(0);
                valuesStr = value instanceof Long ? value + "L" : value.toString();
                return hasAnnotation
                       ? Text.format("%s %s ([%s]%s)", fieldName, relation, annotation, valuesStr)
                       : Text.format("%s %s %s", fieldName, relation, valuesStr);
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

    /**
     * Values contains boolean.
     *
     * @param value the value
     * @return the boolean
     */
    boolean valuesContains(Object value) {
        if (value instanceof String) {
            value = "\"" + value + "\"";
        }
        return values.contains(value);
    }

}
