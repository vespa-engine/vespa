// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Optional;

/**
 * Stores deployment config (backup settings, block windows) derived from the deployment spec
 * at application activation time. Implementations typically write to node-repository ZooKeeper.
 *
 * @author olaa
 */
public interface DeploymentConfigStore {

    /** Stores deployment config for the given application, replacing any previously stored config. */
    void store(ApplicationId applicationId, Optional<BackupConfig> backup, List<BlockWindow> blockWindows);

}
