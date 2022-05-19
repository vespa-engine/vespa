// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.schema.Schema;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import com.yahoo.document.Field;
import com.yahoo.schema.document.SDDocumentType;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author arnej
 */
public class ValidateStructTypeInheritance extends Processor {

    public ValidateStructTypeInheritance(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if (!validate) return;
        verifyNoRedeclarations(schema.getDocument());
    }

    void fail(Field field, String message) {
        throw newProcessException(schema, field, message);
    }

    void verifyNoRedeclarations(SDDocumentType docType) {
        for (SDDocumentType type : docType.allTypes().values()) {
            if (type.isStruct()) {
                var inheritedTypes = new ArrayList<SDDocumentType>(type.getInheritedTypes());
                for (int i = 0; i < inheritedTypes.size(); i++) {
                    SDDocumentType inherit = inheritedTypes.get(i);
                    for (var extra : inherit.getInheritedTypes()) {
                        if (! inheritedTypes.contains(extra)) {
                            inheritedTypes.add(extra);
                        }
                    }
                }
                if (inheritedTypes.isEmpty()) continue;
                var seenFieldNames = new HashSet<>();
                for (var field : type.getDocumentType().contentStruct().getFieldsThisTypeOnly()) {
                    if (seenFieldNames.contains(field.getName())) {
                        // cannot happen?
                        fail(field, "struct "+type.getName()+" has multiple fields with same name: "+field.getName());
                    }
                    seenFieldNames.add(field.getName());
                }
                for (SDDocumentType inherit : inheritedTypes) {
                    if (inherit.isStruct()) {
                        for (var field : inherit.getDocumentType().contentStruct().getFieldsThisTypeOnly()) {
                            if (seenFieldNames.contains(field.getName())) {
                                fail(field, "struct "+type.getName()+" cannot inherit from "+inherit.getName()+" and redeclare field "+field.getName());
                            }
                            seenFieldNames.add(field.getName());
                        }
                    } else {
                        fail(new Field("no field"), "struct cannot inherit from non-struct "+inherit.getName()+" class "+inherit.getClass());
                    }
                }
            }
        }
    }

}
