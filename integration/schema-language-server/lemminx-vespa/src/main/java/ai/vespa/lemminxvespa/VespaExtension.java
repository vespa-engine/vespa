package ai.vespa.lemminxvespa;

import java.io.FileOutputStream;
import java.io.PrintStream;

import javax.xml.validation.Schema;
import javax.xml.validation.Validator;

import org.eclipse.lemminx.dom.DOMDocument;
import org.eclipse.lemminx.extensions.contentmodel.model.ContentModelManager;
import org.eclipse.lemminx.extensions.generators.Grammar;
import org.eclipse.lemminx.services.IXMLValidationService;
import org.eclipse.lemminx.services.extensions.IXMLExtension;
import org.eclipse.lemminx.services.extensions.XMLExtensionsRegistry;
import org.eclipse.lemminx.services.extensions.diagnostics.IDiagnosticsParticipant;
import org.eclipse.lemminx.services.extensions.save.ISaveContext;
import org.eclipse.lemminx.uriresolver.URIResolverExtensionManager;
import org.eclipse.lsp4j.InitializeParams;

import ai.vespa.lemminxvespa.participant.DiagnosticsParticipant;
import ai.vespa.lemminxvespa.participant.HoverParticipant;

/**
 * ServicesPlugin
 * TODO: Figure out class loading of lemminx stuff.
 * TODO: Fetch *.xsd files e.g. ./config-model/target/generated-sources/trang/resources/schema/services.xsd
 *       and use to validate.
 */
public class VespaExtension implements IXMLExtension {
    PrintStream logger = System.out;

    private IDiagnosticsParticipant diagnosticsParticipant;
    private IXMLValidationService validationService;
    private ContentModelManager contentModelManager;


	@Override
	public void doSave(ISaveContext saveContext) {
        if (saveContext.getType() == ISaveContext.SaveContextType.DOCUMENT) {
            logger.println("Saving: " + saveContext.getUri());
            saveContext.collectDocumentToValidate(d -> {
                // TODO
                DOMDocument xml = saveContext.getDocument(d.getDocumentURI());
                xml.resetGrammar();
                return true;
            });
        }
	}

	@Override
	public void start(InitializeParams params, XMLExtensionsRegistry registry) {
        /*
        try {
            logger = new PrintStream(new FileOutputStream("/Users/magnus/repos/integrationtest7/log.txt", true));
        } catch(Exception ex) {
        }



        try {
            URIResolverExtensionManager resolverManager = registry.getComponent(URIResolverExtensionManager.class);
            resolverManager.registerResolver(new VespaURIResolverExtension(logger));
            contentModelManager.getGrammarPool().cacheGrammars("xml", );
            Validator validator;
            contentModelManager = new ContentModelManager(resolverManager);
            registry.registerComponent(contentModelManager);

            diagnosticsParticipant = new DiagnosticsParticipant(this);
            registry.registerDiagnosticsParticipant(diagnosticsParticipant);

            registry.getResolverExtensionManager().registerResolver(new VespaURIResolverExtension(this.logger));
        } catch (Exception ex) {
            ex.printStackTrace(logger);
        }

        //hoverParticipant = new HoverParticipant(this.logger);
        //registry.registerHoverParticipant(hoverParticipant);
        */
	}

	@Override
	public void stop(XMLExtensionsRegistry registry) {
        registry.unregisterDiagnosticsParticipant(diagnosticsParticipant);
        //registry.unregisterHoverParticipant(hoverParticipant);
	}

    public ContentModelManager getContentModelManager() {
        return contentModelManager;
    }

    public PrintStream logger() {
        return this.logger;
    }
}
