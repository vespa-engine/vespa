// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;

import com.yahoo.yolean.concurrent.Sleeper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author bjorncs
 */
class ArtifactProducers {

    private final Map<String, ArtifactProducer> producers;
    private final Map<String, List<ArtifactProducer>> aliases;

    private ArtifactProducers(Set<ArtifactProducer> producers,
                              Map<String, List<Class<? extends ArtifactProducer>>> aliases) {
        var producerMap = producers.stream()
                .collect(Collectors.toMap(ArtifactProducer::artifactName, Function.identity()));
        Map<String, List<ArtifactProducer>> aliasMap = new HashMap<>();
        aliases.forEach((alias, mapping) -> {
            List<ArtifactProducer> concreteMapping = mapping.stream()
                    .map(type -> producers.stream()
                            .filter(p -> p.getClass().equals(type))
                            .findAny()
                            .orElseThrow(() -> new IllegalArgumentException("No producer of type " + type)))
                    .toList();
            if (producerMap.containsKey(alias)) {
                throw new IllegalStateException("Alias name '" + alias + "' conflicts with producer");
            }
            aliasMap.put(alias, concreteMapping);
        });
        this.producers = producerMap;
        this.aliases = aliasMap;
    }

    static ArtifactProducers createDefault(Sleeper sleeper) {
        var producers = Set.of(
                new PerfReporter(),
                new JvmDumper.JavaFlightRecorder(sleeper),
                new JvmDumper.HeapDump(),
                new JvmDumper.Jmap(),
                new JvmDumper.Jstat(),
                new JvmDumper.Jstack(),
                new PmapReporter(),
                new VespaLogDumper(sleeper),
                new ZooKeeperSnapshotDumper());
        var aliases =
                Map.of(
                        "jvm-dump",
                        List.of(
                                JvmDumper.HeapDump.class, JvmDumper.Jmap.class, JvmDumper.Jstat.class,
                                JvmDumper.Jstack.class, VespaLogDumper.class)
                );
        return new ArtifactProducers(producers, aliases);
    }

    static ArtifactProducers createCustom(Set<ArtifactProducer> producers,
                                          Map<String, List<Class<? extends ArtifactProducer>>> aliases) {
        return new ArtifactProducers(producers, aliases);
    }

    List<ArtifactProducer> resolve(List<String> requestedArtifacts) {
        List<ArtifactProducer> resolved = new ArrayList<>();
        for (String artifact : requestedArtifacts) {
            if (aliases.containsKey(artifact)) {
                aliases.get(artifact).stream()
                        .filter(p -> !resolved.contains(p))
                        .forEach(resolved::add);
            } else if (producers.containsKey(artifact)) {
                ArtifactProducer producer = producers.get(artifact);
                if (!resolved.contains(producer)) {
                    resolved.add(producer);
                }
            } else {
                throw createInvalidArtifactException(artifact);
            }
        }
        return resolved;
    }

    private IllegalArgumentException createInvalidArtifactException(String artifact) {
        String producersString = producers.keySet().stream()
                .map(a -> "'" + a + "'")
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
        String aliasesString = aliases.entrySet().stream()
                .map(e -> String.format(
                        "'%s': %s",
                        e.getKey(),
                        e.getValue().stream()
                                .map(p -> "'" + p.artifactName() + "'")
                                .sorted()
                                .collect(Collectors.joining(", ", "[", "]")))
                )
                .collect(Collectors.joining(", ", "[", "]"));
        String msg = String.format(
                "Invalid artifact type '%s'. Valid types are %s and valid aliases are %s",
                artifact, producersString, aliasesString);
        return new IllegalArgumentException(msg);
    }
}
