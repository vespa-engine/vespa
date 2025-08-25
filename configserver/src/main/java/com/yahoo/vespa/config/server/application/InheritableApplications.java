// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A repository of file backed application packages.
 *
 * @author bratseth
 */
public class InheritableApplications {

    private final Map<String, FilesApplicationPackage> applications;

    private InheritableApplications(Map<String, FilesApplicationPackage> applications) {
        this.applications = applications;
    }

    /**
     * Returns the content of this as a map from application id to application package.
     * Ids are on the form "namespace.name".
     */
    public Map<String, FilesApplicationPackage> toMap() {
        return Collections.unmodifiableMap(applications);
    }

    public static InheritableApplications empty() { return new InheritableApplications(Map.of()); }

    /**
     * Imports inheritable applications from a directory where each immediate subdirectory defines a namespace
     * and contains one subdirectory for each application.
     */
    public static class DirectoryImporter {

        private static final Logger log = Logger.getLogger(DirectoryImporter.class.getName());

        public InheritableApplications importFrom(String dir) {
            return importFrom(new File(dir));
        }

        public InheritableApplications importFrom(File dir) {
            if (!dir.exists() || !dir.isDirectory()) {
                log.info("Inherited application directory '" + dir + "' does not exist: No applications can be inherited");
                return InheritableApplications.empty();
            }
            Map<String, FilesApplicationPackage> inheritable = new HashMap<>();
            for (File namespaceDir : dir.listFiles()) {
                if ( ! namespaceDir.isDirectory()) continue;
                inheritable.putAll(importNamespaceFrom(namespaceDir));
            }
            return new InheritableApplications(inheritable);
        }

        private Map<String, FilesApplicationPackage> importNamespaceFrom(File namespaceDir) {
            Map<String, FilesApplicationPackage> inheritable = new HashMap<>();
            for (File applicationDir : namespaceDir.listFiles()) {
                if ( ! applicationDir.isDirectory()) continue;
                inheritable.put(namespaceDir.getName() + "." + applicationDir.getName(),
                                importApplicationFrom(applicationDir));
            }
            return inheritable;
        }

        private FilesApplicationPackage importApplicationFrom(File applicationDir) {
            return FilesApplicationPackage.fromDir(applicationDir, Map.of()); // TODO: 2-pass to allow multilevel inheritance
        }

    }

}
