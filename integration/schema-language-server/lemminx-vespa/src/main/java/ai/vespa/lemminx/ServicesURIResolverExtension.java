package ai.vespa.lemminx;

import java.nio.file.Path;
import java.util.logging.Logger;

import org.eclipse.lemminx.uriresolver.URIResolverExtension;

public class ServicesURIResolverExtension implements URIResolverExtension {
    private static final Logger logger = Logger.getLogger(ServicesURIResolverExtension.class.getName());
    private String resourceURI;

    public ServicesURIResolverExtension(Path serverPath) { 
        resourceURI = serverPath.resolve("resources").resolve("schema").resolve("services.rng").toUri().toString();
        logger.info("Resource URI: " + resourceURI);
    }

    @Override
    public String resolve(String baseLocation, String publicId, String systemId) {
        if (baseLocation != null && baseLocation.endsWith("services.xml")) {
            return resourceURI;
        } else return null;
    }

    public String getSchemaURI() {
        return resourceURI;
    }
}
