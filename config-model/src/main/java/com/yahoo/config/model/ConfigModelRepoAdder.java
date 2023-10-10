// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

/**
 * An interface which provides addition of new config models.
 * This exists because some models need to add additional models during the build phase so *write* access
 * to the config model repo is needed. *Read* access, on the other hand needs to happen through config model dependency
 * injection to avoid circular dependencies or undeclared dependencies working by accident.
 *
 * @author bratseth
 */
public interface ConfigModelRepoAdder {

    void add(ConfigModel model);

}
