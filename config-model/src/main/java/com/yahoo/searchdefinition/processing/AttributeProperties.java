// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Checks that attribute properties only are set for attributes that have data (are created by an indexing statement).
 *
 * @author hmusum
 */
public class AttributeProperties extends Processor {

    public AttributeProperties(Search search, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(search, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate) {
        for (SDField field : search.allConcreteFields()) {
            String fieldName = field.getName();

            // For each attribute, check if the attribute has been created
            // by an indexing statement.
            for (Attribute attribute : field.getAttributes().values()) {
                if (attributeCreated(field, attribute.getName())) {
                    continue;
                }
                // Check other fields or statements that may have created this attribute.
                boolean created = false;
                for (SDField f : search.allConcreteFields()) {
                    // Checking against the field we are looking at
                    if (!f.getName().equals(fieldName)) {
                        if (attributeCreated(f, attribute.getName())) {
                            created = true;
                            break;
                        }
                    }
                }
                if (validate && !created) {
                    throw new IllegalArgumentException("Attribute '" + attribute.getName() + "' in field '" +
                                                       field.getName() + "' is not created by the indexing statement");
                }
            }
        }
    }

    /**
     * Checks if the attribute has been created bye an indexing statement in this field.
     *
     * @param field         a searchdefinition field
     * @param attributeName name of the attribute
     * @return true if the attribute has been created by this field, else false
     */
    static boolean attributeCreated(SDField field, String attributeName) {
        if ( ! field.doesAttributing()) {
            return false;
        }
        for (Attribute attribute : field.getAttributes().values()) {
            if (attribute.getName().equals(attributeName)) {
                return true;
            }
        }
        return false;
    }

}
