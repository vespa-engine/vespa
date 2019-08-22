package ai.vespa.hosted.plugin;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Deploys a Vespa application package to the hosted Vespa API.
 *
 * @author jonmv
 */
@Mojo(name = "delete")
public class DeleteMojo extends AbstractVespaMojo {

    @Parameter(property = "environment")
    private String environment;

    @Parameter(property = "region")
    private String region;

    @Override
    protected void doExecute() {
        if (environment != null && ! Environment.from(environment).isManuallyDeployed())
            throw new IllegalArgumentException("Manual deletion is not permitted in " + environment);

        getLog().info(controller.deactivate(id, zoneOf(environment, region)));
    }

}

