// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.config.provision.Environment;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Deletes a Vespa application deployment, provided the deployment is in a manually deployed environment.
 *
 * @author jonmv
 */
@Mojo(name = "delete")
public class DeleteMojo extends AbstractVespaDeploymentMojo {

    @Override
    protected boolean requireInstance() { return true; }

    @Override
    protected void doExecute() {
        if (!isNullOrBlank(environment) && ! Environment.from(environment).isManuallyDeployed())
            throw new IllegalArgumentException("Manual deletion is not permitted in " + environment);

        getLog().info(controller.deactivate(id, zoneOf(environment, region)));
    }

}

