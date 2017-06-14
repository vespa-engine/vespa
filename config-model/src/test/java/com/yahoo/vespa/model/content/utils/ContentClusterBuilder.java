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

    public static class DocType {
        private final String name;
        private final boolean global;

        public DocType(String name, boolean global) {
            this.name = name;
            this.global = global;
        }

        public DocType(String name) {
            this(name, false);
        }

        public String toXml() {
            return (global ? "<document mode='index' type='" + name + "' global='true'/>" :
                    "<document mode='index' type='" + name + "'/>");
        }
    }

    private String name = "mycluster";
    private int redundancy = 1;
    private int searchableCopies = 1;
    private List<DocType> docTypes = Arrays.asList(new DocType("test"));
    private String groupXml = getSimpleGroupXml();
    private Optional<String> dispatchXml = Optional.empty();
    private Optional<Double> protonDiskLimit = Optional.empty();
    private Optional<Double> protonMemoryLimit = Optional.empty();

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
                map(type -> new DocType(type)).
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

    public ContentClusterBuilder protonDiskLimit(double diskLimit) {
        protonDiskLimit = Optional.of(diskLimit);
        return this;
    }

    public ContentClusterBuilder protonMemoryLimit(double memoryLimit) {
        protonMemoryLimit = Optional.of(memoryLimit);
        return this;
    }

    public ContentCluster build(MockRoot root) throws Exception {
        return ContentClusterUtils.createCluster(getXml(), root);
    }

    public String getXml() {
        String xml = joinLines("<content version='1.0' id='" + name + "'>",
               "  <redundancy>" + redundancy + "</redundancy>",
               "  <documents>",
                docTypes.stream().map(DocType::toXml).collect(Collectors.joining("\n")),
               "  </documents>",
               "  <engine>",
               "    <proton>",
               "      <searchable-copies>" + searchableCopies + "</searchable-copies>",
               getResourceLimitsXml("      "),
               "    </proton>",
               "  </engine>");
        if (dispatchXml.isPresent()) {
            xml += dispatchXml.get();
        }
        return xml + groupXml +
               "</content>";
    }

    private static String getSimpleGroupXml() {
        return joinLines("  <group>",
                "    <node distribution-key='0' hostalias='mockhost'/>",
                "  </group>");
    }

    private String getResourceLimitsXml(String indent) {
        if (protonDiskLimit.isPresent() || protonMemoryLimit.isPresent()) {
            String xml = joinLines(indent + "<resource-limits>",
                    getXmlLine("disk", protonDiskLimit, indent + "  "),
                    getXmlLine("memory", protonMemoryLimit, indent + "  "),
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
