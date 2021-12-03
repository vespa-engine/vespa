// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.content;

import com.yahoo.api.annotations.Beta;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * @author bratseth
 */
@Beta
public class ContentCluster {

    /**
     * Returns mock of the content clusters described in the application at the given path
     *
     * @param path the path to an application package
     * @return a mock content cluster
     */
    public static List<ContentCluster> fromPath(Path path) {
        // new DomContentBuilder().
        // TODO
        return Collections.<ContentCluster>emptyList();
    }

    /*
    public ConfigModel toConfigModel() {
        ConfigModel configModel = new ConfigModel();
        return configModel;
    }
    */

}
