// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.List;
import java.util.Objects;

/**
 * Document declarations to add to an application's content clusters on top of what the application
 * package declares. The programmatic equivalent of {@code <document>} entries in the
 * {@code <documents>} element of services.xml. The declarations name no content
 * cluster: every content cluster of the application declares every type listed here, as if each
 * {@code <documents>} element contained them. Document types the application already declares are
 * left as declared.
 *
 * @author sebasabe
 */
public record AdditionalDocuments(List<DocumentTypeDeclaration> documents) {

    public AdditionalDocuments {
        documents = List.copyOf(documents);
    }

    /**
     * The equivalent of a {@code <document type="..." mode="..." global="..."/>} declaration
     * in a content cluster's {@code <documents>} element. The {@code selection} attribute is not
     * carried: a provider adds a type to have it stored, not a subset of it.
     */
    public record DocumentTypeDeclaration(String type, Mode mode, boolean global) {

        public DocumentTypeDeclaration {
            if (type == null || type.isBlank())
                throw new IllegalArgumentException("A document type declaration must name a type");
            Objects.requireNonNull(mode, "A document type declaration must have a mode");
        }

    }

    /**
     * The values of the {@code mode} attribute of {@code <document>}. Declarations added through this
     * record bypass the services.xml schema validation hand-written ones get, so the values are fixed
     * here instead. Mirrors {@code attribute mode} in {@code content.rnc} and {@code SchemaInfo.IndexMode}
     * in config-model; AdditionalDocumentsTest there fails if they drift.
     */
    public enum Mode {

        INDEX("index"), STREAMING("streaming"), STORE_ONLY("store-only");

        private final String xmlValue;

        Mode(String xmlValue) { this.xmlValue = xmlValue; }

        /** The value as written in services.xml. */
        public String xmlValue() { return xmlValue; }

    }

    public static AdditionalDocuments none() {
        return new AdditionalDocuments(List.of());
    }

    public boolean isEmpty() { return documents.isEmpty(); }

}
