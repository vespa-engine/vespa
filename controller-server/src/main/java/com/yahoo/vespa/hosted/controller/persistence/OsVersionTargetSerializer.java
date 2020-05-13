// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionTarget;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Serializer for {@link com.yahoo.vespa.hosted.controller.versions.OsVersionTarget}.
 *
 * @author mpolden
 */
public class OsVersionTargetSerializer {

    private final OsVersionSerializer osVersionSerializer;

    private static final String versionsField = "versions";
    private static final String upgradeBudgetField = "upgradeBudget";

    public OsVersionTargetSerializer(OsVersionSerializer osVersionSerializer) {
        this.osVersionSerializer = osVersionSerializer;
    }

    public Slime toSlime(Set<OsVersionTarget> osVersionTargets) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray(versionsField);
        osVersionTargets.forEach(target -> toSlime(target, array.addObject()));
        return slime;
    }

    public Set<OsVersionTarget> fromSlime(Slime slime) {
        Inspector array = slime.get().field(versionsField);
        Set<OsVersionTarget> osVersionTargets = new TreeSet<>();
        array.traverse((ArrayTraverser) (i, inspector) -> {
            OsVersion osVersion = osVersionSerializer.fromSlime(inspector);
            Optional<Duration> upgradeBudget = Serializers.optionalDuration(inspector.field(upgradeBudgetField));
            osVersionTargets.add(new OsVersionTarget(osVersion, upgradeBudget));
        });
        return Collections.unmodifiableSet(osVersionTargets);
    }

    private void toSlime(OsVersionTarget target, Cursor object) {
        osVersionSerializer.toSlime(target.osVersion(), object);
        target.upgradeBudget().ifPresent(d -> object.setLong(upgradeBudgetField, d.toMillis()));
    }

}
