// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class for generating Annotation
 * https://docs.vespa.ai/en/reference/query-language-reference.html#annotations
 */
public final class A {

    private final static Annotation EMPTY = new Annotation();

    /**
     * Empty annotation.
     *
     * @return the annotation
     */
    static public Annotation empty() {
        return EMPTY;
    }

    /**
     * Filter annotation.
     *
     * @return the annotation
     */
    static public Annotation filter() {
        return a("filter", true);
    }

    /**
     * Default index annotation.
     *
     * @param index the search index
     * @return the annotation
     */
    static public Annotation defaultIndex(String index) {
        return a("defaultIndex", index);
    }

    /**
     * Arbitrary key-value pair annotation.
     *
     * @param name  the name
     * @param value the value
     * @return the annotation
     */
    public static Annotation a(String name, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(name, value);
        return new Annotation(map);
    }

    /**
     * Arbitrary annotation given by the map.
     *
     * @param annotation the annotation
     * @return the annotation
     */
    public static Annotation a(Map<String, Object> annotation) {
        if (annotation.isEmpty()) {
            return empty();
        }
        return new Annotation(annotation);
    }

    public static Annotation a(String n1, Object v1, String n2, Object v2) {
        return a(new Object[][]{{n1, v1}, {n2, v2}});
    }

    public static Annotation a(String n1, Object v1, String n2, Object v2, String n3, Object v3) {
        return a(new Object[][]{{n1, v1}, {n2, v2}, {n3, v3}});
    }

    public static Annotation a(String n1, Object v1, String n2, Object v2, String n3, Object v3, String n4, Object v4) {
        return a(new Object[][]{{n1, v1}, {n2, v2}, {n3, v3}, {n4, v4}});
    }

    public static Annotation a(String n1, Object v1, String n2, Object v2, String n3, Object v3, String n4, Object v4,
                               String n5, Object v5) {
        return a(new Object[][]{{n1, v1}, {n2, v2}, {n3, v3}, {n4, v4}, {n5, v5}});
    }

    public static Annotation a(String n1, Object v1, String n2, Object v2, String n3, Object v3, String n4, Object v4,
                               String n5, Object v5, String n6, Object v6) {
        return a(new Object[][]{{n1, v1}, {n2, v2}, {n3, v3}, {n4, v4}, {n5, v5}, {n6, v6}});
    }

    private static Annotation a(Object[][] kvpairs) {
        return new Annotation(Stream.of(kvpairs).collect(Collectors.toMap(o -> o[0].toString(), o -> o[1])));
    }

    static boolean hasAnnotation(Annotation annotation) {
        return annotation != null && !EMPTY.equals(annotation);
    }
}
