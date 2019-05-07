// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.versions.OsVersion;
import com.yahoo.vespa.hosted.controller.versions.OsVersionStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Serializer for {@link OsVersionStatus}.
 *
 * @author mpolden
 */
public class OsVersionStatusSerializer {

    private static final String versionsField = "versions";
    private static final String versionField = "version";
    private static final String nodesField = "nodes";
    private static final String hostnameField = "hostname";
    private static final String regionField = "region";
    private static final String environmentField = "environment";

    private final OsVersionSerializer osVersionSerializer;

    public OsVersionStatusSerializer(OsVersionSerializer osVersionSerializer) {
        this.osVersionSerializer = Objects.requireNonNull(osVersionSerializer, "osVersionSerializer must be non-null");
    }

    public Slime toSlime(OsVersionStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor versions = root.setArray(versionsField);
        status.versions().forEach((version, nodes) -> {
            Cursor object = versions.addObject();
            osVersionSerializer.toSlime(version, object);
            nodesToSlime(nodes, object.setArray(nodesField));
        });
        return slime;
    }

    public OsVersionStatus fromSlime(Slime slime) {
        return new OsVersionStatus(osVersionsFromSlime(slime.get().field(versionsField)));
    }

    private void nodesToSlime(List<OsVersionStatus.Node> nodes, Cursor array) {
        nodes.forEach(node -> nodeToSlime(node, array.addObject()));
    }

    private void nodeToSlime(OsVersionStatus.Node node, Cursor object) {
        object.setString(hostnameField, node.hostname().value());
        object.setString(versionField, node.version().toFullString());
        object.setString(regionField, node.region().value());
        object.setString(environmentField, node.environment().value());
    }

    private Map<OsVersion, List<OsVersionStatus.Node>> osVersionsFromSlime(Inspector array) {
        Map<OsVersion, List<OsVersionStatus.Node>> versions = new TreeMap<>();
        array.traverse((ArrayTraverser) (i, object) -> {
            OsVersion osVersion = osVersionSerializer.fromSlime(object);
            List<OsVersionStatus.Node> nodes = nodesFromSlime(object.field(nodesField));
            versions.put(osVersion, nodes);
        });
        return Collections.unmodifiableMap(versions);
    }

    private List<OsVersionStatus.Node> nodesFromSlime(Inspector array) {
        List<OsVersionStatus.Node> nodes = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, object) -> nodes.add(nodeFromSlime(object)));
        return Collections.unmodifiableList(nodes);
    }

    private OsVersionStatus.Node nodeFromSlime(Inspector object) {
        return new OsVersionStatus.Node(
                HostName.from(object.field(hostnameField).asString()),
                Version.fromString(object.field(versionField).asString()),
                Environment.from(object.field(environmentField).asString()),
                RegionName.from(object.field(regionField).asString())
        );
    }

}
