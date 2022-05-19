// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.TemporarySDDocumentType;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

/**
 * @author arnej
 */
public class TemporarySDTypeResolver {

    private final DeployLogger deployLogger;
    private final Collection<Schema> toProcess;
    private final List<SDDocumentType> docTypes = new LinkedList<>();

    public TemporarySDTypeResolver(Collection<Schema> schemas, DeployLogger deployLogger) {
        this.deployLogger = deployLogger;
        this.toProcess = schemas;
    }

    private SDDocumentType findDocType(String name) {
        assert(name != null);
        for (var doc : docTypes) {
            if (doc.getName().equals(name)) {
                return doc;
            }
        }
        deployLogger.logApplicationPackage(Level.WARNING, "No document type in application matching name: "+name);
        return null;
    }

    public void process() {
        docTypes.add(SDDocumentType.VESPA_DOCUMENT);
        for (Schema schema : toProcess) {
            if (schema.hasDocument()) {
                docTypes.add(schema.getDocument());
            }
        }
        // first, fix inheritance
        for (SDDocumentType doc : docTypes) {
            for (SDDocumentType inherited : doc.getInheritedTypes()) {
                if (inherited instanceof TemporarySDDocumentType) {
                    var actual = findDocType(inherited.getName());
                    if (actual != null) {
                        doc.inherit(actual);
                    } else {
                        deployLogger.logApplicationPackage(Level.WARNING, "Unresolved inherit '"+inherited.getName() +"' for document "+doc.getName());
                    }
                }
            }
        }
        // next, check owned types (structs only?)
        for (SDDocumentType doc : docTypes) {
            for (SDDocumentType owned : doc.getTypes()) {
                if (owned instanceof TemporarySDDocumentType) {
                    deployLogger.logApplicationPackage(Level.WARNING, "Schema '"+doc.getName()+"' owned type '"+owned.getName()+"' is temporary, should not happen");
                    continue;
                }
                for (SDDocumentType inherited : owned.getInheritedTypes()) {
                    if (inherited instanceof TemporarySDDocumentType) {
                        var actual = doc.getType(inherited.getName());
                        if (actual != null) {
                            owned.inherit(actual);
                        } else {
                            deployLogger.logApplicationPackage(Level.WARNING, "Unresolved inherit '"+inherited.getName() +"' for type '"+owned.getName()+"' in document "+doc.getName());
                        }
                    }
                }
            }
        }
    }

}
