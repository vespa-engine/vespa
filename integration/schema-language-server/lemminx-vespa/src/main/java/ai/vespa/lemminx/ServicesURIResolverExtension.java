package ai.vespa.lemminx;

import java.io.File;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.eclipse.lemminx.uriresolver.URIResolverExtension;

public class ServicesURIResolverExtension implements URIResolverExtension {
    private PrintStream logger;
    private String resourceURI;

    public ServicesURIResolverExtension(Path serverPath, PrintStream logger) { 
        this.logger = logger; 
        resourceURI = serverPath.resolve("resources").resolve("schema").resolve("services.rng").toUri().toString();
        this.logger.println("Using resource URI: " + resourceURI);
    }

    @Override
    public String resolve(String baseLocation, String publicId, String systemId) {
        // TODO: verify workspace root to cooperate with schema-language-server.
        if (baseLocation != null && baseLocation.endsWith("services.xml")) {
            return resourceURI;
        } else return null;
    }
}
