// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.model;

import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.ModelCreateResult;
import com.yahoo.config.model.api.ValidationParameters;
import com.yahoo.component.Version;
import com.yahoo.vespa.model.VespaModelFactory;

/**
 * @author Ulf Lilleengen
 */
public class TestModelFactory extends VespaModelFactory {

    private final Version vespaVersion;
    private ModelContext modelContext;

    public TestModelFactory(Version vespaVersion) {
        this(new NullConfigModelRegistry(), vespaVersion);
    }

    public TestModelFactory(ConfigModelRegistry registry, Version vespaVersion) {
        super(registry);
        this.vespaVersion = vespaVersion;
    }

    // Needed for testing (to get hold of ModelContext)
    @Override
    public ModelCreateResult createAndValidateModel(ModelContext modelContext, ValidationParameters validationParameters) {
        this.modelContext = modelContext;
        return super.createAndValidateModel(modelContext, validationParameters);
    }

    @Override
    public Version version() {
        return vespaVersion;
    }

    public ModelContext getModelContext() {
        return modelContext;
    }

}
