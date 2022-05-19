// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;
import com.yahoo.document.config.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Facade for deriving configs from schemas
 *
 * @author bratseth
 */
public class Deriver {

    public static ApplicationBuilder getSchemaBuilder(List<String> schemas) {
        ApplicationBuilder builder = new ApplicationBuilder();
        try {
            for (String schema : schemas)
                builder.addSchemaFile(schema);
        } catch (ParseException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        builder.build(true);
        return builder;
    }

    public static DocumentmanagerConfig.Builder getDocumentManagerConfig(String sd) {
        return getDocumentManagerConfig(Collections.singletonList(sd));
    }

    public static DocumentmanagerConfig.Builder getDocumentManagerConfig(List<String> schemas) {
        return new DocumentManager().produce(getSchemaBuilder(schemas).getModel(), new DocumentmanagerConfig.Builder());
    }

    public static DocumenttypesConfig.Builder getDocumentTypesConfig(String schema) {
        return getDocumentTypesConfig(Collections.singletonList(schema));
    }

    public static DocumenttypesConfig.Builder getDocumentTypesConfig(List<String> schemas) {
        return new DocumentTypes().produce(getSchemaBuilder(schemas).getModel(), new DocumenttypesConfig.Builder());
    }

}
