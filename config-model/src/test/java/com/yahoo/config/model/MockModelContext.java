// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.application.provider.StaticConfigDefinitionRepo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;

import java.util.Optional;

/**
* @author hmusum
*/
public class MockModelContext implements ModelContext {

    private final ApplicationPackage applicationPackage;

    public MockModelContext() {
        this.applicationPackage = MockApplicationPackage.createEmpty();
    }

    public MockModelContext(ApplicationPackage applicationPackage) {
        this.applicationPackage = applicationPackage;
    }

    @Override
    public ApplicationPackage applicationPackage() {
        return applicationPackage;
    }

    @Override
    public Optional<Model> previousModel() {
        return Optional.empty();
    }

    @Override
    public Optional<ApplicationPackage> permanentApplicationPackage() {
        return Optional.empty();
    }

    @Override
    public HostProvisioner getHostProvisioner() {
        return DeployState.getDefaultModelHostProvisioner(applicationPackage);
    }

    @Override
    public Provisioned provisioned() { return new Provisioned(); }

    @Override
    public DeployLogger deployLogger() {
        return new BaseDeployLogger();
    }

    @Override
    public ConfigDefinitionRepo configDefinitionRepo() {
        return new StaticConfigDefinitionRepo();
    }

    @Override
    public FileRegistry getFileRegistry() {
        return new MockFileRegistry();
    }

    @Override
    public Version modelVespaVersion() { return new Version(6); }

    @Override
    public Version wantedNodeVespaVersion() { return new Version(6); }

    @Override
    public Properties properties() {
        return new TestProperties();
    }
}
