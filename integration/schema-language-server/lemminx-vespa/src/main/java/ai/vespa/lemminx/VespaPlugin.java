package ai.vespa.lemminx;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lemminx.services.IXMLValidationService;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;

public class VespaPlugin implements IXMLExtension {

    PrintStream logger;
    HoverParticipant hoverParticipant;
    IXMLValidationService validationService;
    URIResolverExtension uriResolverExtension;
    IDefinitionParticipant definitionParticipant;
    IDocumentLifecycleParticipant documentLifecycleParticipant;
    Path serverPath;

    @Override
	public void doSave(ISaveContext context) { }

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        try {
            logger = new PrintStream(new FileOutputStream("/Users/magnus/repos/integrationtest7/log.txt", true));
            logger.println("Asserted dominance");
            serverPath = Paths.get(VespaPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

            UnpackRNGFiles.unpackRNGFiles(
                serverPath,
                logger
            );
        } catch (Exception ex) {
            if (logger != null) {
                logger.println("Exception occured during start: " + ex.getMessage());
            }
            return;
        }

        hoverParticipant = new HoverParticipant(logger);
        uriResolverExtension = new ServicesURIResolverExtension(serverPath, logger);
        definitionParticipant = new DefinitionParticipant(logger, registry.getCommandService());
        documentLifecycleParticipant = new DocumentLifecycleParticipant(logger, registry.getCommandService());
        registry.getResolverExtensionManager().registerResolver(uriResolverExtension);
        registry.registerHoverParticipant(hoverParticipant);
        registry.registerDefinitionParticipant(definitionParticipant);
        registry.registerDocumentLifecycleParticipant(documentLifecycleParticipant);

	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
        if (hoverParticipant != null) {
            registry.unregisterHoverParticipant(hoverParticipant);
        }

        if (uriResolverExtension != null) {
            registry.getResolverExtensionManager().unregisterResolver(uriResolverExtension);
        }

        if (definitionParticipant != null) {
            registry.unregisterDefinitionParticipant(definitionParticipant);
        }

        if (documentLifecycleParticipant != null) {
            registry.unregisterDocumentLifecycleParticipant(documentLifecycleParticipant);
        }
	}
}
