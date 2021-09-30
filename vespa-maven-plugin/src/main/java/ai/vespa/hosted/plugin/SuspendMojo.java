// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Sets suspension status for a Vespa application deployment.
 *
 * @author jonmv
 */
@Mojo(name = "suspend")
public class SuspendMojo extends AbstractVespaDeploymentMojo {

    @Parameter(property = "suspend", required = true)
    private boolean suspend;

    @Override
    protected boolean requireInstance() { return true; }

    @Override
    protected void doExecute() {
        getLog().info(controller.suspend(id, zoneOf(environment, region), suspend));
    }

}
