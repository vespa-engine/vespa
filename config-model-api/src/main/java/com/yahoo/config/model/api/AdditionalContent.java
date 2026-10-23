// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.io.reader.NamedReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Content to add to an application on top of what its package declares: schemas, and the
 * content cluster document declarations of those schemas. The two are supplied together
 * because they must match: every declared type must be one of the supplied schemas, checked
 * here by schema file name, so an application never gets a declaration of a type it has no
 * schema for, nor a schema it cannot store.
 *
 * <p>The declarations are the programmatic equivalent of {@code <document>} entries in the
 * {@code <documents>} element of services.xml. They name no content cluster: every content
 * cluster of the application declares every type listed here, as if each {@code <documents>}
 * element contained them. Document types the application already declares are left as
 * declared.</p>
 *
 * @author sebasabe
 */
public record AdditionalContent(List<NamedReader> schemas, List<DocumentTypeDeclaration> documents) {

    public AdditionalContent {
        schemas = List.copyOf(schemas);
        documents = List.copyOf(documents);
        Set<String> schemaNames = new HashSet<>();
        for (NamedReader schema : schemas)
            schemaNames.add(schemaName(schema.getName()));
        List<String> undeclared = new ArrayList<>();
        for (DocumentTypeDeclaration declaration : documents)
            if ( ! schemaNames.contains(declaration.type())) undeclared.add(declaration.type());
        if ( ! undeclared.isEmpty())
            throw new IllegalArgumentException("Additional document declarations " + undeclared + " have no schema among " +
                                               "the additional schemas " + schemaNames.stream().sorted().toList() +
                                               ": a declared type must have a schema file named '<type>.sd' supplied with it");
    }

    /**
     * The schema name a schema file name denotes: the name without directories and extension, so
     * {@code schemas/product.sd} names {@code product}. The same rule the schema parser applies
     * when it requires the file name to match the schema it contains.
     */
    private static String schemaName(String fileName) {
        int slash = fileName.lastIndexOf('/');
        if (slash != -1) fileName = fileName.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        if (dot != -1) fileName = fileName.substring(0, dot);
        return fileName;
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
     * in config-model; AdditionalContentTest there should fail if they drift.
     */
    public enum Mode {

        INDEX("index"), STREAMING("streaming"), STORE_ONLY("store-only");

        private final String xmlValue;

        Mode(String xmlValue) { this.xmlValue = xmlValue; }

        /** The value as written in services.xml. */
        public String xmlValue() { return xmlValue; }

    }

    public static AdditionalContent none() {
        return new AdditionalContent(List.of(), List.of());
    }

    public boolean isEmpty() { return schemas.isEmpty() && documents.isEmpty(); }

}
