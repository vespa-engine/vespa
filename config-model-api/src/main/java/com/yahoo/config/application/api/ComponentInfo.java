// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;


/**
 * Describes a component residing in the components directory.
 *
 * @author tonytv
 */
// TODO: add support for component versions.
public class ComponentInfo {

    final String pathRelativeToAppDir;

    public ComponentInfo(String pathRelativeToAppDir) {
        this.pathRelativeToAppDir = pathRelativeToAppDir;
    }

    //get path relative to app dir
    public String getPathRelativeToAppDir() {
        return pathRelativeToAppDir;
    }

    @Override
    public String toString() { return "component at '" + pathRelativeToAppDir + "'"; }

}
