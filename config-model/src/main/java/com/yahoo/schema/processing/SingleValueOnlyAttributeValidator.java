// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.container.search.QueryProfiles;

/**
 * Validates attribute fields using bool type, ensuring the collection type is supported.
 *
 * Currently, only the single value bool type is supported.
 *
 * @author geirst
 */
public class SingleValueOnlyAttributeValidator extends Processor {

    private static final boolean allowArrayOfBoolFromEnv =
            Boolean.parseBoolean(System.getenv("VESPA_ALLOW_ARRAY_OF_BOOL"));

    public SingleValueOnlyAttributeValidator(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        processInternal(allowArrayOfBoolFromEnv);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly, ModelContext.Properties properties) {
        boolean allowArrayOfBool = allowArrayOfBoolFromEnv ||
                                   isEnvironmentVariableEnabled(properties, "VESPA_ALLOW_ARRAY_OF_BOOL");
        processInternal(allowArrayOfBool);
    }

    private void processInternal(boolean allowArrayOfBool) {
        for (var field : schema.allConcreteFields()) {
            var attribute = field.getAttribute();
            if (attribute == null) {
                continue;
            }

            if (attribute.getType().equals(Attribute.Type.BOOL) &&
                    attribute.getCollectionType().equals(Attribute.CollectionType.ARRAY) &&
                    allowArrayOfBool) {
                continue;
            }

            if ((attribute.getType().equals(Attribute.Type.BOOL) ||
                    attribute.getType().equals(Attribute.Type.RAW)) &&
                    !attribute.getCollectionType().equals(Attribute.CollectionType.SINGLE)) {
                fail(schema, field, "Only single value " + attribute.getType().getName() + " attribute fields are supported");
            }
        }
    }

    private boolean isEnvironmentVariableEnabled(ModelContext.Properties properties, String varName) {
        if (properties == null) {
            return false;
        }
        for (String envVar : properties.environmentVariables()) {
            if (envVar.startsWith(varName + "=")) {
                String value = envVar.substring(varName.length() + 1);
                return Boolean.parseBoolean(value);
            }
        }
        return false;
    }
}
