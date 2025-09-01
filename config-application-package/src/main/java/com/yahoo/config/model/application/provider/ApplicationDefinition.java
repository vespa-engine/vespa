// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.io.IOUtils;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the content of the optional application.xml file in application packages.
 *
 * @author bratseth
 */
public class ApplicationDefinition {

    private final List<String> inherited;

    private ApplicationDefinition(List<String> inherited) {
        this.inherited = List.copyOf(inherited);
    }

    public List<String> inherited() { return inherited; }

    public List<FilesApplicationPackage> resolveInherited(Map<String, FilesApplicationPackage> inheritableApplications) {
        List<FilesApplicationPackage> inheritedPackages = new ArrayList<>();
        for (String inheritedId : inherited) {
            var inheritedPackage = inheritableApplications.get(inheritedId);
            if (inheritedPackage == null)
                throw new IllegalArgumentException("Inherited application '" + inheritedId + "' does not exist. " +
                                                   "Available applications: " + inheritableApplications.keySet());
            inheritedPackages.add(inheritedPackage);
        }
        return inheritedPackages;
    }

    public static ApplicationDefinition empty() {
        return new ApplicationDefinition(List.of());
    }

    public static class XmlReader {

        public ApplicationDefinition read(Optional<Reader> reader) {
            return reader.isEmpty() ? ApplicationDefinition.empty() : read(reader.get());
        }

        public ApplicationDefinition read(Reader reader) {
            try {
                return read(IOUtils.readAll(reader));
            }
            catch (IOException e) {
                throw new IllegalArgumentException("Could not read application definition", e);
            }
        }

        public ApplicationDefinition read(String xmlForm) {
            Element root = XML.getDocument(xmlForm).getDocumentElement();
            return new ApplicationDefinition(readInherited(root));
        }

        private List<String> readInherited(Element root) {
            Element inherits = XML.getChild(root, "inherits");
            if (inherits == null) return List.of();
            return Arrays.stream(XML.getValue(inherits).split(" ")).map(String::trim).toList();
        }

    }

}
