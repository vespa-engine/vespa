// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;
import com.yahoo.document.DocumenttypesConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.UnprocessingSearchBuilder;
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

    /**
     * Derives only document manager.
     *
     *
     * @param sdFileNames The name of the search definition files to derive from.
     * @param toDir       The directory to write configuration to.
     * @return The list of Search objects, possibly "unproper ones", from sd files containing only document
     */
    public static SearchBuilder deriveDocuments(List<String> sdFileNames, String toDir) {
        SearchBuilder builder = getUnprocessingSearchBuilder(sdFileNames);
        DocumentmanagerConfig.Builder documentManagerCfg = new DocumentManager().produce(builder.getModel(), new DocumentmanagerConfig.Builder());
        try {
            DerivedConfiguration.exportDocuments(documentManagerCfg, toDir);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return builder;
    }

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

    public static SearchBuilder getUnprocessingSearchBuilder(List<String> sds) {
        SearchBuilder builder = new UnprocessingSearchBuilder();
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
