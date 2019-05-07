package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.Deployment;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Paths;

/**
 * Deploys a Vespa application package to the hosted Vespa API.
 *
 * @author jonmv
 */
@Mojo(name = "deploy")
public class DeployMojo extends AbstractVespaMojo {

    @Parameter(property = "applicationZip")
    private String applicationZip;

    @Parameter(property = "vespaVersion")
    private String vespaVersion;

    @Parameter(property = "ignoreValidationErrors")
    private String ignoreValidationErrors;

    @Parameter(property = "environment")
    private String environment;

    @Parameter(property = "region")
    private String region;

    @Parameter(property = "repository")
    private String repository;

    @Parameter(property = "branch")
    private String branch;

    @Parameter(property = "commit")
    private String commit;

    @Parameter(property = "build")
    private Long build;

    @Override
    protected void doExecute() {
        Deployment deployment = build == null
                ? Deployment.ofPackage(Paths.get(firstNonBlank(applicationZip, projectPathOf("target", "application.zip"))))
                : Deployment.ofReference(repository, branch, commit, build);
        if ("true".equalsIgnoreCase(ignoreValidationErrors)) deployment = deployment.ignoringValidationErrors();
        if (vespaVersion != null) deployment = deployment.atVersion(vespaVersion);

        ZoneId zone = environment == null || region == null ? controller.devZone() : ZoneId.from(environment, region);

        System.out.println(controller.deploy(deployment, id, zone).json());
    }

}
