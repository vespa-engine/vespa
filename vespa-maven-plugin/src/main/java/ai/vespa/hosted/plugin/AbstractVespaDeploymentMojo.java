// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.plugin;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for hosted Vespa plugin mojos which refer to a particular deployment.
 *
 * @author jonmv
 */
public abstract class AbstractVespaDeploymentMojo extends AbstractVespaMojo {

    @Parameter(property = "environment")
    protected String environment;

    @Parameter(property = "region")
    protected String region;

    protected ZoneId zoneOf(String environment, String region) {
        if (region == null)
            return controller.defaultZone(environment != null ? Environment.from(environment)
                                                              : Environment.dev);

        if (environment == null)
            throw new IllegalArgumentException("Environment must be specified if region is specified");

        return ZoneId.from(environment, region);
    }

}
