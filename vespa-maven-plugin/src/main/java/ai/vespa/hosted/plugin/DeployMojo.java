package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.Deployment;
import ai.vespa.hosted.api.DeploymentLog;
import ai.vespa.hosted.api.DeploymentResult;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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
public class DeployMojo extends AbstractVespaDeploymentMojo {

    @Parameter(property = "applicationZip")
    private String applicationZip;

    @Parameter(property = "vespaVersion")
    private String vespaVersion;

    @Parameter(property = "follow", defaultValue = "true")
    private boolean follow;

    @Parameter(property = "vespaLogLevel", defaultValue = "error")
    private String vespaLogLevel;
    private DeploymentLog.Level loggable;

    @Override
    protected void doExecute() throws MojoFailureException, MojoExecutionException {
        loggable = DeploymentLog.Level.valueOf(vespaLogLevel);
        Deployment deployment = Deployment.ofPackage(Paths.get(firstNonBlank(applicationZip,
                                                                             projectPathOf("target", "application.zip"))));
        if (vespaVersion != null) deployment = deployment.atVersion(vespaVersion);

        ZoneId zone = zoneOf(environment, region);
        try {
            DeploymentResult result = controller.deploy(deployment, id, zone);
            getLog().info(result.message());
            if (follow) tailLogs(id, zone, result.run());
        }
        catch (IllegalArgumentException e) {
            throw new MojoFailureException( "The application " + id.tenant() + "." + id.application() + " does not exist, "
                                            + "or you do not have the required privileges to access it; "
                                            + "please visit the Vespa cloud web UI and make sure the application exists and you have access!");
        }
    }

    private void tailLogs(ApplicationId id, ZoneId zone, long run) throws MojoFailureException, MojoExecutionException {
        long last = -1;
        DeploymentLog log;
        while (true) {
            log = controller.deploymentLog(id, zone, run, last);
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
        switch (log.status()) {
            case success:            return;
            case error:              throw new MojoExecutionException("Unexpected error during deployment; see log for details");
            case aborted:            throw new MojoFailureException("Deployment was aborted, probably by a newer deployment");
            case outOfCapacity:      throw new MojoFailureException("No capacity left in zone; please contact the Vespa team");
            case deploymentFailed:   throw new MojoFailureException("Deployment failed; see log for details");
            case installationFailed: throw new MojoFailureException("Installation failed; see Vespa log for details");
            case running:            throw new MojoFailureException("Deployment not completed");
            case testFailure:        throw new IllegalStateException("Unexpected status; tests are not run for manual deployments");
            default:                 throw new IllegalArgumentException("Unexpected status '" + log.status() + "'");
        }
    }

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final String padding = "\n" + " ".repeat(23);

    private void print(DeploymentLog.Entry entry) {
        String timestamp = formatter.format(entry.at());
        String message = String.join(padding, entry.message().split("\n"))
                               .replaceAll("\\s*\n", "\n").trim();
        if ( ! entry.isVespaLogEntry() || loggable.compareTo(entry.level()) >= 0)
            switch (entry.level()) {
                case error   : getLog().error("   [" + timestamp + "]  " + message); break;
                case warning : getLog().warn   (" [" + timestamp + "]  " + message); break;
                case info    : getLog().info("    [" + timestamp + "]  " + message); break;
                default      : getLog().debug("   [" + timestamp + "]  " + message); break;
            }
    }

}
