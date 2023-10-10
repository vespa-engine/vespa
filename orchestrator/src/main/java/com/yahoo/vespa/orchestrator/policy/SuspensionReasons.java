// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.ServiceInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Information worth logging when/if suspending a host.
 *
 * @author hakon
 */
public class SuspensionReasons {
    private final Map<HostName, List<String>> reasons = new HashMap<>();

    public static SuspensionReasons nothingNoteworthy() { return new SuspensionReasons(); }

    public static SuspensionReasons isDown(ServiceInstance service) {
        return new SuspensionReasons().addReason(
                service.hostName(),
                service.descriptiveName() + " is down");
    }

    public static SuspensionReasons unknownStatus(ServiceInstance service) {
        return new SuspensionReasons().addReason(
                service.hostName(),
                service.descriptiveName() + " has not yet been probed for health");
    }

    public static SuspensionReasons downSince(ServiceInstance service, Instant instant, Duration downDuration) {
        return new SuspensionReasons().addReason(
                service.hostName(),
                service.descriptiveName() + " has been down since " +
                        // Round to whole second
                        Instant.ofEpochSecond(instant.getEpochSecond()).toString() +
                        " (" + downDuration.getSeconds() + " seconds)");
    }

    public SuspensionReasons() {}

    /** An ordered list of all messages, typically useful for testing. */
    public List<String> getMessagesInOrder() {
        return reasons.values().stream().flatMap(Collection::stream).sorted().toList();
    }

    public SuspensionReasons mergeWith(SuspensionReasons that) {
        for (var entry : that.reasons.entrySet()) {
            for (var reason : entry.getValue()) {
                addReason(entry.getKey(), reason);
            }
        }

        return this;
    }

    /**
     * Makes a log message, if there is anything worth logging about the decision to allow suspension,
     * that can be directly fed to {@link java.util.logging.Logger#info(String) Logger.info(String)}.
     */
    public Optional<String> makeLogMessage() {
        if (reasons.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(reasons.entrySet().stream()
                .map(entry -> entry.getKey().s() + " suspended because " + String.join(", ", entry.getValue()))
                .sorted()
                .collect(Collectors.joining("; ")));
    }

    /** Package-private for testing. */
    SuspensionReasons addReason(HostName hostname, String message) {
        reasons.computeIfAbsent(hostname, h -> new ArrayList<>()).add(message);
        return this;
    }

    @Override
    public String toString() {
        return makeLogMessage().orElse("");
    }
}
