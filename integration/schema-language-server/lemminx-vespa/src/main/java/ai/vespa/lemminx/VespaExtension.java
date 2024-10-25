package ai.vespa.lemminx;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.eclipse.lemminx.services.IXMLValidationService;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lsp4j.InitializeParams;

public class VespaExtension implements IXMLExtension {
    private static final Logger logger = Logger.getLogger(VespaExtension.class.getName());

    HoverParticipant hoverParticipant;
    IXMLValidationService validationService;
    URIResolverExtension uriResolverExtension;
    IDefinitionParticipant definitionParticipant;
    IDocumentLifecycleParticipant documentLifecycleParticipant;
    IDiagnosticsParticipant diagnosticsParticipant;
    Path serverPath;

    @Override
	public void doSave(ISaveContext context) { }

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        try {
            serverPath = Paths.get(VespaExtension.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();

            UnpackRNGFiles.unpackRNGFiles(
                serverPath
            );
        } catch (Exception ex) {
            if (logger != null) {
                logger.severe("Exception occured during start: " + ex.getMessage());
            }
            return;
        }

        SchemaLSCommands.init(registry.getCommandService());

        hoverParticipant             = new HoverParticipant(serverPath);
        uriResolverExtension         = new ServicesURIResolverExtension(serverPath);
        definitionParticipant        = new DefinitionParticipant();
        documentLifecycleParticipant = new DocumentLifecycleParticipant(registry.getCommandService());
        diagnosticsParticipant       = new DiagnosticsParticipant();

        registry.getResolverExtensionManager().registerResolver(uriResolverExtension);
        registry.registerHoverParticipant(hoverParticipant);
        registry.registerDefinitionParticipant(definitionParticipant);
        registry.registerDocumentLifecycleParticipant(documentLifecycleParticipant);
        registry.registerDiagnosticsParticipant(diagnosticsParticipant);

        logger.info("Vespa LemminX extension activated");

	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
        // calls unregister for each registered extension
        registry.dispose();
	}
}
