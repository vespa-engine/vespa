package ai.vespa.hosted.plugin;

import ai.vespa.hosted.api.ControllerHttpClient;
import com.yahoo.config.provision.ApplicationId;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Base class for hosted Vespa plugin mojos.
 *
 * @author jonmv
 */
public abstract class AbstractVespaMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(property = "endpoint", defaultValue = "https://api.vespa.corp.yahoo.com:4443") // TODO jvenstad: Change default
    protected String endpointUri;

    @Parameter(property = "tenant")
    protected String tenant;

    @Parameter(property = "application")
    protected String application;

    @Parameter(property = "instance")
    protected String instance;

    @Parameter(property = "privateKeyFile", required = true)
    protected String privateKeyFile;

    @Parameter(property = "certificateFile")
    protected String certificateFile;

    // Fields set up as part of setup().
    protected ApplicationId id;
    protected ControllerHttpClient controller;

    @Override
    public final void execute() {
        setup();
        doExecute();
    }

    /** Override this in subclasses, instead of {@link #execute()}. */
    protected abstract void doExecute();

    protected void setup() {
        tenant = firstNonBlank(tenant, project.getProperties().getProperty("tenant"));
        application = firstNonBlank(application, project.getProperties().getProperty("application"));
        instance = firstNonBlank(instance, project.getProperties().getProperty("instance"), "default");
        id = ApplicationId.from(tenant, application, instance);

        controller = certificateFile == null
                ? ControllerHttpClient.withSignatureKey(URI.create(endpointUri), Paths.get(privateKeyFile), id)
                : ControllerHttpClient.withKeyAndCertificate(URI.create(endpointUri), Paths.get(privateKeyFile), Paths.get(certificateFile));
    }

    protected String projectPathOf(String first, String... rest) {
        return project.getBasedir().toPath().resolve(Path.of(first, rest)).toString();
    }

    /** Returns the first of the given strings which is non-null and non-blank, or throws IllegalArgumentException. */
    protected static String firstNonBlank(String... values) {
        for (String value : values)
            if (value != null && ! value.isBlank())
                return value;

        throw new IllegalArgumentException("No valid value given");
    }

}
