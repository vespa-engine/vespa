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

/**
 * Serializer for OS version status.
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

    public Slime toSlime(OsVersionStatus status) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor versions = root.setArray(versionsField);
        status.versions().forEach(version -> osVersionToSlime(version, versions.addObject()));
        return slime;
    }

    public OsVersionStatus fromSlime(Slime slime) {
        return new OsVersionStatus(osVersionsFromSlime(slime.get().field(versionsField)));
    }

    private void osVersionToSlime(OsVersion version, Cursor object) {
        object.setString(versionField, version.version().toFullString());
        nodesToSlime(version.nodes(), object.setArray(nodesField));
    }

    private void nodesToSlime(List<OsVersion.Node> nodes, Cursor array) {
        nodes.forEach(node -> nodeToSlime(node, array.addObject()));
    }

    private void nodeToSlime(OsVersion.Node node, Cursor object) {
        object.setString(hostnameField, node.hostname().value());
        object.setString(versionField, node.version().toFullString());
        object.setString(regionField, node.region().value());
        object.setString(environmentField, node.environment().value());
    }

    private List<OsVersion> osVersionsFromSlime(Inspector array) {
        List<OsVersion> versions = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, object) -> versions.add(osVersionFromSlime(object)));
        return Collections.unmodifiableList(versions);
    }

    private OsVersion osVersionFromSlime(Inspector object) {
        return new OsVersion(
                Version.fromString(object.field(versionField).asString()),
                nodesFromSlime(object.field(nodesField))
        );
    }

    private List<OsVersion.Node> nodesFromSlime(Inspector array) {
        List<OsVersion.Node> nodes = new ArrayList<>();
        array.traverse((ArrayTraverser) (i, object) -> nodes.add(nodeFromSlime(object)));
        return Collections.unmodifiableList(nodes);
    }

    private OsVersion.Node nodeFromSlime(Inspector object) {
        return new OsVersion.Node(
                HostName.from(object.field(hostnameField).asString()),
                Version.fromString(object.field(versionField).asString()),
                Environment.from(object.field(environmentField).asString()),
                RegionName.from(object.field(regionField).asString())
        );
    }

}
