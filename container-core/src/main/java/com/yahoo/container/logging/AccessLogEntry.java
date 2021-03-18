// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.logging;

import com.yahoo.collections.ListMap;
import com.yahoo.yolean.trace.TraceNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * <p>Information to be logged in the access log.</p>
 *
 * <p>This class contains the union of all information that can be
 * logged with all the supported access log formats.</p>
 *
 * <p>The add methods can be called multiple times,
 * but the parameters should be different for each
 * invocation of the same method.</p>
 *
 * This class is thread-safe.
 *
 * @author Tony Vaagenes
 * @author bakksjo
 * @author bjorncs
 */
public class AccessLogEntry {

    private final Object monitor = new Object();

    private HitCounts hitCounts;
    private TraceNode traceNode;
    private ListMap<String,String> keyValues=null;

    public void setHitCounts(final HitCounts hitCounts) {
        synchronized (monitor) {
            requireNull(this.hitCounts);
            this.hitCounts = hitCounts;
        }
    }

    public HitCounts getHitCounts() {
        synchronized (monitor) {
            return hitCounts;
        }
    }

    public void addKeyValue(String key,String value) {
        synchronized (monitor) {
            if (keyValues == null) {
                keyValues = new ListMap<>();
            }
            keyValues.put(key,value);
        }
    }

    public Map<String, List<String>> getKeyValues() {
        synchronized (monitor) {
            if (keyValues == null) {
                return null;
            }

            final Map<String, List<String>> newMapWithImmutableValues = mapValues(
                    keyValues.entrySet(),
                    valueList -> Collections.unmodifiableList(new ArrayList<>(valueList)));
            return Collections.unmodifiableMap(newMapWithImmutableValues);
        }
    }

    private static <K, V1, V2> Map<K, V2> mapValues(
            final Set<Map.Entry<K, V1>> entrySet,
            final Function<V1, V2> valueConverter) {
        return entrySet.stream()
                .collect(toMap(
                        entry -> entry.getKey(),
                        entry -> valueConverter.apply(entry.getValue())));
    }

    public void setTrace(TraceNode traceNode) {
        synchronized (monitor) {
            requireNull(this.traceNode);
            this.traceNode = traceNode;
        }
    }

    public TraceNode getTrace() {
        synchronized (monitor) {
            return traceNode;
        }
    }

    @Override
    public String toString() {
        return "AccessLogEntry{" +
                "hitCounts=" + hitCounts +
                ", traceNode=" + traceNode +
                ", keyValues=" + keyValues +
                '}';
    }

    private static void requireNull(final Object value) {
        if (value != null) {
            throw new IllegalStateException("Attempt to overwrite field that has been assigned. Value: " + value);
        }
    }

}
