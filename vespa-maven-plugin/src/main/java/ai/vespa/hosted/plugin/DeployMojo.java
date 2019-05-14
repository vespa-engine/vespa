package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.Deployment;
import ai.vespa.hosted.api.DeploymentLog;
import ai.vespa.hosted.api.DeploymentResult;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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

    @Parameter(property = "follow", defaultValue = "true")
    private boolean follow;

    @Override
    protected void doExecute() {
        Deployment deployment = build == null
                ? Deployment.ofPackage(Paths.get(firstNonBlank(applicationZip, projectPathOf("target", "application.zip"))))
                : Deployment.ofReference(repository, branch, commit, build);
        if ("true".equalsIgnoreCase(ignoreValidationErrors)) deployment = deployment.ignoringValidationErrors(); // TODO unused, GC or fix.
        if (vespaVersion != null) deployment = deployment.atVersion(vespaVersion);

        ZoneId zone = environment == null || region == null ? controller.devZone() : ZoneId.from(environment, region);

        DeploymentResult result = controller.deploy(deployment, id, zone);
        getLog().info(result.message());

        if (follow) tailLogs(id, zone, result.run());
    }

    private void tailLogs(ApplicationId id, ZoneId zone, long run) {
        long last = -1;
        while (true) {
            DeploymentLog log = controller.deploymentLog(id, zone, run, last);
            for (DeploymentLog.Entry entry : log.entries())
                print(entry);
            last = log.last().orElse(last);

            if ( ! log.isActive())
                break;

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    private void print(DeploymentLog.Entry entry) {
        String timestamp = formatter.format(entry.at());
        switch (entry.level()) {
            case "warning" : getLog().warn(" [" + timestamp + "]  " + entry.message()); break;
            case "error" : getLog().error("[" + timestamp + "]  " + entry.message()); break;
            default: getLog().info(" [" + timestamp + "]  " + entry.message()); break;
        }
    }

}
