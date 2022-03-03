// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class resolving some inheritance relationships.
 *
 * @author arnej27959
 **/
public class InheritanceResolver {

    private final Map<String, ParsedSchema> parsedSchemas;
    private final Map<String, ParsedDocument> parsedDocs = new HashMap<>();

    public InheritanceResolver(Map<String, ParsedSchema> parsedSchemas) {
        this.parsedSchemas = parsedSchemas;
    }

    private void inheritanceCycleCheck(ParsedSchema schema, List<String> seen) {
        String name = schema.name();
        if (seen.contains(name)) {
            seen.add(name);
            throw new IllegalArgumentException("Inheritance cycle for schemas: " +
                                               String.join(" -> ", seen));
        }
        seen.add(name);
        for (ParsedSchema parent : schema.getAllResolvedInherits()) {
            inheritanceCycleCheck(parent, seen);
        }
        seen.remove(name);
    }

    private void resolveSchemaInheritance() {
        for (ParsedSchema schema : parsedSchemas.values()) {
            for (String inherit : schema.getInherited()) {
                var parent = parsedSchemas.get(inherit);
                if (parent == null) {
                    throw new IllegalArgumentException("schema '" + schema.name() + "' inherits '" + inherit + "', but this schema does not exist");
                }
                schema.resolveInherit(inherit, parent);
            }
        }
    }

    private void checkSchemaCycles() {
        List<String> seen = new ArrayList<>();
        for (ParsedSchema schema : parsedSchemas.values()) {
            inheritanceCycleCheck(schema, seen);
        }
    }

    private void resolveDocumentInheritance() {
        for (ParsedSchema schema : parsedSchemas.values()) {
            if (! schema.hasDocument()) {
                throw new IllegalArgumentException("For schema '" + schema.name() +
                                                   "': A search specification must have an equally named document inside of it.");
            }
            ParsedDocument doc = schema.getDocument();
            var old = parsedDocs.put(doc.name(), doc);
            if (old != null) {
                throw new IllegalArgumentException("duplicate document declaration for " + doc.name());
            }
            for (String docInherit : doc.getInherited()) {
                schema.inheritByDocument(docInherit);
            }
        }
        for (ParsedDocument doc : parsedDocs.values()) {
            for (String inherit : doc.getInherited()) {
                var parentDoc = parsedDocs.get(inherit);
                if (parentDoc == null) {
                    throw new IllegalArgumentException("document " + doc.name() + " inherits from unavailable document " + inherit);
                }
                doc.resolveInherit(inherit, parentDoc);
            }
        }
        for (ParsedSchema schema : parsedSchemas.values()) {
            for (String inherit : schema.getInheritedByDocument()) {
                var parent = parsedSchemas.get(inherit);
                assert(parent.hasDocument());
                assert(parent.getDocument().name().equals(inherit));
                schema.resolveInheritByDocument(inherit, parent);
            }
        }
    }

    private void inheritanceCycleCheck(ParsedDocument document, List<String> seen) {
        String name = document.name();
        if (seen.contains(name)) {
            seen.add(name);
            throw new IllegalArgumentException("Inheritance cycle for documents: " +
                                               String.join(" -> ", seen));
        }
        seen.add(name);
        for (ParsedDocument parent : document.getResolvedInherits()) {
            inheritanceCycleCheck(parent, seen);
        }
        seen.remove(name);
    }

    private void checkDocumentCycles() {
        List<String> seen = new ArrayList<>();
        for (ParsedDocument doc : parsedDocs.values()) {
            inheritanceCycleCheck(doc, seen);
        }
    }

    public void resolveInheritance() {
        resolveSchemaInheritance();
        resolveDocumentInheritance();
        checkDocumentCycles();
        checkSchemaCycles();
    }

}
