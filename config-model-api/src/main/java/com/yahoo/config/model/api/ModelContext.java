// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.yahoo.component.Version;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Rotation;
import com.yahoo.config.provision.Zone;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Model context containing state provided to model factories.
 *
 * @author lulf
 */
public interface ModelContext {

    ApplicationPackage applicationPackage();
    Optional<Model> previousModel();
    Optional<ApplicationPackage> permanentApplicationPackage();
    Optional<HostProvisioner> hostProvisioner();
    DeployLogger deployLogger();
    ConfigDefinitionRepo configDefinitionRepo();
    FileRegistry getFileRegistry();
    Properties properties();
    default Optional<File> appDir() { return Optional.empty();}
    
    /** @deprecated TODO: Remove this when no config models older than 6.98 are used */
    @SuppressWarnings("unused")
    @Deprecated
    default Optional<com.yahoo.config.provision.Version> vespaVersion() { return Optional.empty(); }
    
    /** The Vespa version this model is built for */
    Version modelVespaVersion();
    
    /** The Vespa version we want nodes to become */
    Version wantedNodeVespaVersion();

    interface Properties {
        boolean multitenant();
        ApplicationId applicationId();
        List<ConfigServerSpec> configServerSpecs();
        boolean hostedVespa();
        Zone zone();
        Set<Rotation> rotations();
    }
}
