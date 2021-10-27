// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    protected ZoneId zone;

    @Parameter(property = "environment")
    protected String environment;

    @Parameter(property = "region")
    protected String region;

    protected ZoneId zoneOf(String environment, String region) {
        if (isNullOrBlank(region))
            return zone = controller.defaultZone(isNullOrBlank(environment) ? Environment.dev
                                                                            : Environment.from(environment));

        if (isNullOrBlank(environment))
            throw new IllegalArgumentException("Environment must be specified if region is specified");

        return zone = ZoneId.from(environment, region);
    }

    @Override
    protected String name() {
        return super.name() + "." + instance + " in " +
               (zone != null ? zone.region() + " in " + zone.environment()
                             : (region == null ? "default region" : region) + " in " +
                               (environment == null ? "default environment (dev)" : environment));
    }

}
