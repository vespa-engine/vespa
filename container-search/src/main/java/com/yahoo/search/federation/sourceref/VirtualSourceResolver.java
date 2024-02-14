// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation.sourceref;

import com.yahoo.search.federation.FederationConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Multiple sources like contentcluster.schema1, contencluster.schema2 needs to respond
 * when source is the prefix contentcluster. This is done by generating map from virtual source
 * to the fully qualified ones, and resolving from there.
 *
 * @author baldersheim
 */
public class VirtualSourceResolver {
    private final Map<String, Set<String>> virtualSources;
    private VirtualSourceResolver(Map<String, Set<String>> virtualSources) {
        this.virtualSources = virtualSources;
    }
    public static VirtualSourceResolver of() {
        return new VirtualSourceResolver(Map.of());
    }
    public static VirtualSourceResolver of(Set<String> targets) {
        return new VirtualSourceResolver(createVirtualSources(targets));
    }
    private static Map<String, Set<String>> createVirtualSources(Set<String> targets) {
        Set<String> virtualSources = targets.stream()
                .filter(id -> id.contains("."))
                .map(id -> id.substring(0, id.indexOf('.')))
                .collect(Collectors.toUnmodifiableSet());
        if (virtualSources.isEmpty()) return Map.of();
        Map<String, Set<String>> virtualSourceMap = new HashMap<>();
        for (String virtualSource : virtualSources) {
            String prefix = virtualSource + ".";
            Set<String> sources = targets.stream()
                    .filter(id -> id.startsWith(prefix))
                    .collect(Collectors.toUnmodifiableSet());
            virtualSourceMap.put(virtualSource, sources);
        }
        return virtualSourceMap;
    }
    public static VirtualSourceResolver of(FederationConfig config) {
        return of(config.target().stream().map(FederationConfig.Target::id).collect(Collectors.toUnmodifiableSet()));
    }
    public Set<String> resolve(Set<String> sourcesInQuery) {
        boolean hasMapping = sourcesInQuery.stream().anyMatch(virtualSources::containsKey);
        if (hasMapping) {
            Set<String> resolved = new HashSet<>();
            for (String source : sourcesInQuery) {
                var subSources = virtualSources.get(source);
                if (subSources != null) {
                    resolved.addAll(subSources);
                } else {
                    resolved.add(source);
                }
            }
            return resolved;
        }
        return sourcesInQuery;
    }
}
