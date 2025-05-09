package ai.vespa.lemminx;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import org.eclipse.lemminx.XMLLanguageServer;
import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.services.IXMLValidationService;
import org.eclipse.lemminx.services.extensions.IDefinitionParticipant;
import org.eclipse.lemminx.services.extensions.IDocumentLifecycleParticipant;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.codeaction.ICodeActionParticipant;
import org.eclipse.lemminx.services.extensions.completion.ICompletionParticipant;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.uriresolver.URIResolverExtension;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.InitializeParams;

import ai.vespa.lemminx.command.SchemaLSCommands;
import ai.vespa.lemminx.index.ServiceDocument;
import ai.vespa.lemminx.participants.CodeActionParticipant;
import ai.vespa.lemminx.participants.CompletionParticipant;
import ai.vespa.lemminx.participants.DefinitionParticipant;
import ai.vespa.lemminx.participants.DiagnosticsParticipant;
import ai.vespa.lemminx.participants.DocumentLifecycleParticipant;
import ai.vespa.lemminx.participants.HoverParticipant;

public class VespaExtension implements IXMLExtension {
    private static final Logger logger = Logger.getLogger(VespaExtension.class.getName());

    HoverParticipant hoverParticipant;
    IXMLValidationService validationService;
    ServicesURIResolverExtension uriResolverExtension;
    IDefinitionParticipant definitionParticipant;
    IDocumentLifecycleParticipant documentLifecycleParticipant;
    IDiagnosticsParticipant diagnosticsParticipant;
    ICodeActionParticipant codeActionParticipant;
    ICompletionParticipant completionParticipant;
    Path serverPath;

    @Override
    public void doSave(ISaveContext context) {
    }

    @Override
    public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        try {
            serverPath = Paths.get(new File(
                VespaExtension.class.getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI()
            ).getCanonicalPath()).getParent();

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

        ServiceDocument serviceDocument = new ServiceDocument();

        hoverParticipant             = new HoverParticipant(serverPath);
        uriResolverExtension         = new ServicesURIResolverExtension(serverPath);
        definitionParticipant        = new DefinitionParticipant();
        documentLifecycleParticipant = new DocumentLifecycleParticipant(registry.getCommandService(), serviceDocument);
        diagnosticsParticipant       = new DiagnosticsParticipant(uriResolverExtension);
        codeActionParticipant        = new CodeActionParticipant();
        completionParticipant        = new CompletionParticipant();


        registry.getResolverExtensionManager().registerResolver(uriResolverExtension);
        registry.registerHoverParticipant(hoverParticipant);
        registry.registerDefinitionParticipant(definitionParticipant);
        registry.registerDocumentLifecycleParticipant(documentLifecycleParticipant);
        registry.registerDiagnosticsParticipant(diagnosticsParticipant);
        registry.registerCodeActionParticipant(codeActionParticipant);
        registry.registerCompletionParticipant(completionParticipant);

        logger.info("Vespa LemminX extension activated");
    }

    @Override
    public void stop(XMLExtensionsRegistry registry) {
        if (uriResolverExtension != null) 
            registry.getResolverExtensionManager().unregisterResolver(uriResolverExtension);
        if (hoverParticipant != null)
            registry.unregisterHoverParticipant(hoverParticipant);
        if (definitionParticipant != null)
            registry.unregisterDefinitionParticipant(definitionParticipant);
        if (documentLifecycleParticipant != null)
            registry.unregisterDocumentLifecycleParticipant(documentLifecycleParticipant);
        if (diagnosticsParticipant != null)
            registry.unregisterDiagnosticsParticipant(diagnosticsParticipant);
        if (codeActionParticipant != null)
            registry.unregisterCodeActionParticipant(codeActionParticipant);
        if (completionParticipant != null)
            registry.unregisterCompletionParticipant(completionParticipant);
    }

    /*
     * Returns true if we manage this document
     */
    public static boolean match(DOMDocument xmlDocument) {
        if (xmlDocument == null) return false;
        String uri = xmlDocument.getDocumentURI();
        if (uri == null) return false;
        return uri.endsWith("services.xml");
    }
}
