// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.application.provider;

import com.yahoo.collections.Tuple2;
import com.yahoo.config.application.api.ApplicationPackage;

import java.io.File;

/**
 * Definitions of sub-directories of an application package.
 *
 * @author hmusum
 */
public class AppSubDirs {

    private final Tuple2<File, String> root;
    private final Tuple2<File, String> routingtables;
    private final Tuple2<File, String> configDefs;

    public AppSubDirs(File root) {
        this.root = new Tuple2<>(root, root.getName());
        routingtables = createTuple(ApplicationPackage.ROUTINGTABLES_DIR);
        configDefs = createTuple(ApplicationPackage.CONFIG_DEFINITIONS_DIR);
    }

    private Tuple2<File, String> createTuple(String name) {
        return new Tuple2<>(file(name), name);
    }

    public File file(String subPath) {
        return new File(root.first, subPath);
    }

    public File root() {
        return root.first;
    }

    public File configDefs() {
        return configDefs.first;
    }
    public Tuple2<File, String> routingTables() {
        return routingtables;
    }

}
