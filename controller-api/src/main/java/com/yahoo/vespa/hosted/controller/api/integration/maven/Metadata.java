// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.maven;

import com.yahoo.component.Version;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Metadata about a released artifact.
 *
 * @author jonmv
 */
public class Metadata {

    private final ArtifactId id;
    private final List<Version> versions;

    public Metadata(ArtifactId id, List<Version> versions) {
        this.id = requireNonNull(id);
        this.versions = versions.stream().sorted().toList();
    }

    /** Creates a new Metadata object from the given XML document. */
    public static Metadata fromXml(String xml) {
        Element metadata = XML.getDocument(xml).getDocumentElement();
        ArtifactId id = new ArtifactId(XML.getValue(XML.getChild(metadata, "groupId")),
                                       XML.getValue(XML.getChild(metadata, "artifactId")));
        List<Version> versions = new ArrayList<>();
        for (Element version : XML.getChildren(XML.getChild(XML.getChild(metadata, "versioning"), "versions")))
            versions.add(Version.fromString(XML.getValue(version)));

        return new Metadata(id, versions);
    }

    /** Id of the metadata this concerns. */
    public ArtifactId id() { return id; }

    /** List of available versions of this, sorted by ascending version order. */
    public List<Version> versions() { return versions; }

}
