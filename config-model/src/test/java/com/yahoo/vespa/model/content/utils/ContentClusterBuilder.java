// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.utils;

import com.yahoo.config.model.test.MockRoot;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.config.model.test.TestUtil.joinLines;

/**
 * Class for building a content cluster with indexed search (used for testing only).
 *
 * @author geirst
 */
public class ContentClusterBuilder {

    private String name = "mycluster";
    private int redundancy = 1;
    private int searchableCopies = 1;
    private List<DocType> docTypes = Arrays.asList(DocType.index("test"));
    private String groupXml = getSimpleGroupXml();
    private Optional<String> dispatchXml = Optional.empty();
    private Optional<Double> protonDiskLimit = Optional.empty();
    private Optional<Double> protonMemoryLimit = Optional.empty();
    private Optional<Double> clusterControllerDiskLimit = Optional.empty();
    private Optional<Double> clusterControllerMemoryLimit = Optional.empty();

    public ContentClusterBuilder() {
    }

    public ContentClusterBuilder name(String name) {
        this.name = name;
        return this;
    }

    public ContentClusterBuilder redundancy(int redundancy) {
        this.redundancy = redundancy;
        return this;
    }

    public ContentClusterBuilder searchableCopies(int searchableCopies) {
        this.searchableCopies = searchableCopies;
        return this;
    }

    public ContentClusterBuilder docTypes(String ... docTypes) {
        this.docTypes = Arrays.asList(docTypes).stream().
                map(type -> DocType.index(type)).
                collect(Collectors.toList());
        return this;
    }

    public ContentClusterBuilder docTypes(List<DocType> docTypes) {
        this.docTypes = docTypes;
        return this;
    }

    public ContentClusterBuilder groupXml(String groupXml) {
        this.groupXml = groupXml;
        return this;
    }

    public ContentClusterBuilder dispatchXml(Optional<String> dispatchXml) {
        this.dispatchXml = dispatchXml;
        return this;
    }

    public ContentClusterBuilder protonDiskLimit(double limit) {
        protonDiskLimit = Optional.of(limit);
        return this;
    }

    public ContentClusterBuilder protonMemoryLimit(double limit) {
        protonMemoryLimit = Optional.of(limit);
        return this;
    }

    public ContentClusterBuilder clusterControllerDiskLimit(double limit) {
        clusterControllerDiskLimit = Optional.of(limit);
        return this;
    }

    public ContentClusterBuilder clusterControllerMemoryLimit(double limit) {
        clusterControllerMemoryLimit = Optional.of(limit);
        return this;
    }

    public ContentCluster build(MockRoot root) {
        return ContentClusterUtils.createCluster(getXml(), root);
    }

    public String getXml() {
        String xml = joinLines("<content version='1.0' id='" + name + "'>",
               "  <redundancy>" + redundancy + "</redundancy>",
               DocType.listToXml(docTypes),
               "  <engine>",
               "    <proton>",
               "      <searchable-copies>" + searchableCopies + "</searchable-copies>",
               getProtonResourceLimitsXml("      "),
               "    </proton>",
               "  </engine>");
        if (dispatchXml.isPresent()) {
            xml += dispatchXml.get();
        }
        xml += groupXml;
        xml += joinLines("  <tuning>",
               getTuningResourceLimitsXml("    "),
               "  </tuning>");
        return xml + "</content>";
    }

    private static String getSimpleGroupXml() {
        return joinLines("  <group>",
                "    <node distribution-key='0' hostalias='mockhost'/>",
                "  </group>");
    }

    private String getProtonResourceLimitsXml(String indent) {
        return getResourceLimitsXml(indent, protonDiskLimit, protonMemoryLimit);
    }

    private String getTuningResourceLimitsXml(String indent) {
        return getResourceLimitsXml(indent, clusterControllerDiskLimit, clusterControllerMemoryLimit);
    }

    private String getResourceLimitsXml(String indent, Optional<Double> diskLimit, Optional<Double> memoryLimit) {
        if (diskLimit.isPresent() || memoryLimit.isPresent()) {
            String xml = joinLines(indent + "<resource-limits>",
                    getXmlLine("disk", diskLimit, indent + "  "),
                    getXmlLine("memory", memoryLimit, indent + "  "),
                    indent + "</resource-limits>");
            return xml;
        }
        return "";
    }

    private static String getXmlLine(String tag, Optional<Double> value, String indent) {
        if (value.isPresent()) {
            return indent + "<" + tag + ">" + value.get() + "</" + tag + ">\n";
        }
        return "";
    }

}
