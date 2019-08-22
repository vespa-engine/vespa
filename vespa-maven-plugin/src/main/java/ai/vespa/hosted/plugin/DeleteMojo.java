package ai.vespa.hosted.plugin;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Deletes a Vespa application deployment, provided the deployment is in a manually deployed environment.
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

