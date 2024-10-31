package ai.vespa.lemminx;

import java.nio.file.Path;

import org.eclipse.lemminx.uriresolver.URIResolverExtension;

public class ServicesURIResolverExtension implements URIResolverExtension {
    private String resourceURI;

    public ServicesURIResolverExtension(Path serverPath) { 
        resourceURI = serverPath.resolve("resources").resolve("schema").resolve("services.rng").toUri().toString();
    }

    @Override
    public String resolve(String baseLocation, String publicId, String systemId) {
        if (baseLocation != null && baseLocation.endsWith("services.xml")) {
            return resourceURI;
        } else return null;
    }
}
