// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A repository of file backed application packages.
 *
 * @author bratseth
 */
public class ApplicationRepo {

    private final Map<String, FilesApplicationPackage> applications = new HashMap<>();

    public ApplicationRepo() {
        applications.putAll(createInternalApplications());
    }

    /**
     * Returns the content of this as a map from application id to application package.
     * Ids are on the form "namespace.name".
     */
    public Map<String, FilesApplicationPackage> toMap() {
        return Collections.unmodifiableMap(applications);
    }

    private Map<String, FilesApplicationPackage> createInternalApplications() {
        return Map.of(); // TODO
    }

}
