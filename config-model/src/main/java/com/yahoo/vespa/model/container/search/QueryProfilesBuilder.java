// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.io.reader.NamedReader;
import com.yahoo.search.query.profile.config.QueryProfileXMLReader;
import com.yahoo.config.application.api.ApplicationPackage;

import java.util.Collections;
import java.util.List;

/**
 * Reads the query profile and query profile types from an application package. The actual reading
 * is delegated to a {@link com.yahoo.search.query.profile.config.QueryProfileXMLReader}.
 *
 * @author bratseth
 */
public class QueryProfilesBuilder {

    /** Build the set of query profiles for an application package */
    public QueryProfiles build(ApplicationPackage applicationPackage) {
        List<NamedReader> queryProfileTypeFiles = null;
        List<NamedReader> queryProfileFiles = null;
        try {
            queryProfileTypeFiles = applicationPackage.getQueryProfileTypeFiles();
            queryProfileFiles = applicationPackage.getQueryProfileFiles();
            return new QueryProfiles(new QueryProfileXMLReader().read(queryProfileTypeFiles, queryProfileFiles));
        }
        finally {
            NamedReader.closeAll(queryProfileTypeFiles);
            NamedReader.closeAll(queryProfileFiles);
        }
    }

}
