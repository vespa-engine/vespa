// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.model.api.ConfigDefinitionRepo;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.application.provider.StaticConfigDefinitionRepo;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Zone;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public Optional<HostProvisioner> hostProvisioner() {
        return Optional.empty();
    }

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
        return new Properties() {
            @Override
            public boolean multitenant() {
                return false;
            }

            @Override
            public ApplicationId applicationId() {
                return ApplicationId.defaultId();
            }

            @Override
            public List<ConfigServerSpec> configServerSpecs() {
                return Collections.emptyList();
            }

            @Override
            public HostName loadBalancerName() {
                return null;
            }

            @Override
            public URI ztsUrl() {
                return null;
            }

            @Override
            public String athenzDnsSuffix() {
                return null;
            }

            @Override
            public boolean hostedVespa() {return false; }

            @Override
            public Zone zone() {
                return Zone.defaultZone();
            }

            @Override
            public Set<Rotation> rotations() {
                return new HashSet<>();
            }
        };
    }
}
