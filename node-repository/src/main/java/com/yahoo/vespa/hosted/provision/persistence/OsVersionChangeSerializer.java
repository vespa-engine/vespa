// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.os.OsVersionChange;
import com.yahoo.vespa.hosted.provision.os.OsVersionTarget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;

/**
 * Serializer for {@link OsVersionChange}.
 *
 * @author mpolden
 */
public class OsVersionChangeSerializer {

    private static final String TARGETS_FIELD = "targets";
    private static final String NODE_TYPE_FIELD = "nodeType";
    private static final String VERSION_FIELD = "version";
    private static final String UPGRADE_BUDGET_FIELD = "upgradeBudget";
    private static final String LAST_RETIRED_AT_FIELD = "lastRetiredAt";

    private OsVersionChangeSerializer() {}

    public static byte[] toJson(OsVersionChange change) {
        var slime = new Slime();
        var object = slime.setObject();
        var targetsObject = object.setArray(TARGETS_FIELD);
        change.targets().forEach((nodeType, target) -> {
            var targetObject = targetsObject.addObject();
            targetObject.setString(NODE_TYPE_FIELD, NodeSerializer.toString(nodeType));
            targetObject.setString(VERSION_FIELD, target.version().toFullString());
            target.upgradeBudget().ifPresent(duration -> targetObject.setLong(UPGRADE_BUDGET_FIELD, duration.toMillis()));
            target.lastRetiredAt().ifPresent(instant -> targetObject.setLong(LAST_RETIRED_AT_FIELD, instant.toEpochMilli()));
            // TODO(mpolden): Stop writing old format after May 2020
            var versionObject = object.setObject(NodeSerializer.toString(nodeType));
            versionObject.setString(VERSION_FIELD, target.version().toFullString());
        });

        try {
            return SlimeUtils.toJsonBytes(slime);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static OsVersionChange fromJson(byte[] data) {
        var targets = new HashMap<NodeType, OsVersionTarget>();
        var inspector = SlimeUtils.jsonToSlime(data).get();
        // TODO(mpolden): Remove handling of old format after May 2020
        inspector.traverse((ObjectTraverser) (key, value) -> {
            if (isNodeType(key)) {
                Version version = Version.fromString(value.field(VERSION_FIELD).asString());
                OsVersionTarget target = new OsVersionTarget(NodeType.valueOf(key), version, Optional.empty(),
                                                             Optional.empty());
                targets.put(NodeSerializer.nodeTypeFromString(key), target);
            }
        });
        inspector.field(TARGETS_FIELD).traverse((ArrayTraverser) (idx, arrayInspector) -> {
            var version = Version.fromString(arrayInspector.field(VERSION_FIELD).asString());
            var nodeType = NodeSerializer.nodeTypeFromString(arrayInspector.field(NODE_TYPE_FIELD).asString());
            Optional<Duration> budget = optionalLong(arrayInspector.field(UPGRADE_BUDGET_FIELD)).map(Duration::ofMillis);
            Optional<Instant> lastRetiredAt = optionalLong(arrayInspector.field(LAST_RETIRED_AT_FIELD)).map(Instant::ofEpochMilli);
            targets.put(nodeType, new OsVersionTarget(nodeType, version, budget, lastRetiredAt));
        });
        return new OsVersionChange(targets);
    }

    private static boolean isNodeType(String name) {
        try {
            NodeType.valueOf(name);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Optional<Long> optionalLong(Inspector field) {
        return field.valid() ? Optional.of(field.asLong()) : Optional.empty();
    }

}
