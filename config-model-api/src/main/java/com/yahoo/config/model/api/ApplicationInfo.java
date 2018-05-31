// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.config.provision.ApplicationId;

public class ApplicationInfo {

    private final ApplicationId applicationId;
    private final long generation;
    private final Model model;  // NOT immutable

    public ApplicationInfo(ApplicationId applicationId, long generation, Model model) {
        this.applicationId = applicationId;
        this.generation = generation;
        this.model = model;
    }

    public ApplicationId getApplicationId() {
        return applicationId;
    }
    public long getGeneration() {
        return generation;
    }
    public Model getModel() {
        return model;
    }

}
