// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;
import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import com.yahoo.vespa.configmodel.producers.DocumentTypes;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Auxiliary facade for deriving configs from search definitions
 *
 * @author bratseth
 */
public class Deriver {

    public static SearchBuilder getSearchBuilder(List<String> sds) {
        SearchBuilder builder = new SearchBuilder();
        try {
            for (String s : sds) {
                builder.importFile(s);
            }
        } catch (ParseException | IOException e) {
            throw new IllegalArgumentException(e);
        }
        builder.build();
        return builder;
    }

    public static DocumentmanagerConfig.Builder getDocumentManagerConfig(String sd) {
        return getDocumentManagerConfig(Collections.singletonList(sd));
    }

    public static DocumentmanagerConfig.Builder getDocumentManagerConfig(List<String> sds) {
        return new DocumentManager().produce(getSearchBuilder(sds).getModel(), new DocumentmanagerConfig.Builder());
    }

    public static DocumenttypesConfig.Builder getDocumentTypesConfig(String sd) {
        return getDocumentTypesConfig(Collections.singletonList(sd));
    }

    public static DocumenttypesConfig.Builder getDocumentTypesConfig(List<String> sds) {
        return new DocumentTypes().produce(getSearchBuilder(sds).getModel(), new DocumenttypesConfig.Builder());
    }

}
