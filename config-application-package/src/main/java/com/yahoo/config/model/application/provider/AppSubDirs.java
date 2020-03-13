// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    final Tuple2<File, String> root;
    public final Tuple2<File, String> rules;
    final Tuple2<File, String> searchchains;
    final Tuple2<File, String> docprocchains;
    final Tuple2<File, String> routingtables;
    final Tuple2<File, String> configDefs;
    final Tuple2<File, String> searchdefinitions;

    public AppSubDirs(File root) {
        this.root = new Tuple2<>(root, root.getName());
        rules = createTuple(ApplicationPackage.RULES_DIR.getRelative());
        searchchains = createTuple(ApplicationPackage.SEARCHCHAINS_DIR);
        docprocchains = createTuple(ApplicationPackage.DOCPROCCHAINS_DIR);
        routingtables = createTuple(ApplicationPackage.ROUTINGTABLES_DIR);
        configDefs = createTuple(ApplicationPackage.CONFIG_DEFINITIONS_DIR);
        searchdefinitions = createTuple(ApplicationPackage.SEARCH_DEFINITIONS_DIR.getRelative());
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

    public File rules() {
        return rules.first;
    }

    public File searchchains() {
        return searchchains.first;
    }

    public File docprocchains() {
        return docprocchains.first;
    }

    public File configDefs() {
        return configDefs.first;
    }

    public File searchdefinitions() {
        return searchdefinitions.first;
    }

}
