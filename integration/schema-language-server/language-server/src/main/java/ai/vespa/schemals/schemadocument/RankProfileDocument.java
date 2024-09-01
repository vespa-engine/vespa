package ai.vespa.schemals.schemadocument;

import java.util.Optional;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.schemadocument.resolvers.InheritanceResolver;
import ai.vespa.schemals.schemadocument.resolvers.ResolverTraversal;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * RankProfileDocumnet parses and represents .profile files
 */
public class RankProfileDocument implements DocumentManager {
    private ClientLogger logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private SchemaDocumentScheduler scheduler;
    private SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    private String fileURI;
    private String content = null;
    private Integer version;
    private boolean isOpen = false;
    private SchemaNode CST = null;

    public RankProfileDocument(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler, String fileURI) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.scheduler = scheduler;
        this.fileURI = fileURI;
    }

    @Override
    public void updateFileContent(String content, Integer version) {
        this.version = version;
        this.updateFileContent(content);
    }

    @Override
    public void updateFileContent(String content) {
        this.content = content;

        this.schemaIndex.clearDocument(this.fileURI);

        ParseContext context = new ParseContext(content, logger, fileURI, schemaIndex, this.scheduler);
        context.useRankProfileIdentifiers();
        var result = parseContent(context);

        diagnosticsHandler.publishDiagnostics(this.fileURI, result.diagnostics());
        if (result.CST().isPresent()) {
            this.CST = result.CST().get();
            lexer.setCST(CST);
        }
    }

    public static SchemaDocument.ParseResult parseContent(ParseContext context) {
        CharSequence sequence = context.content();
        SchemaParser parserFaultTolerant = new SchemaParser(context.fileURI(), sequence);
        parserFaultTolerant.setParserTolerant(true);

        try {
            parserFaultTolerant.RootRankProfile();
        } catch(ParseException pe) {
            // ignore
        } catch(IllegalArgumentException e) {
            // ignore
        }

        Node node = parserFaultTolerant.rootNode();
        var tolerantResult = SchemaDocument.parseCST(node, context);

        tolerantResult.diagnostics().addAll(InheritanceResolver.resolveInheritances(context));

        if (tolerantResult.CST().isPresent()) {
            tolerantResult.diagnostics().addAll(ResolverTraversal.traverse(context, tolerantResult.CST().get()));
        }

        return tolerantResult;
    }

    /*
     * Returns the definition of the schema this rank-profile belongs to.
     */
    public Optional<Symbol> schemaSymbol() {
        if (CST == null || CST.size() == 0) return Optional.empty();
        try {
            Symbol rankProfileDefinition = CST.get(0).get(1).getSymbol();
            return schemaIndex.getFirstSymbolDefinition(rankProfileDefinition.getScope());
        } catch(Exception e) {}
        return Optional.empty(); 
    }

    @Override
    public String getFileURI() {
        return this.fileURI;
    }

	@Override
	public void reparseContent() {
        if (this.content != null) {
            this.updateFileContent(this.content);
        }
	}

	@Override
	public boolean setIsOpen(boolean isOpen) {
        this.isOpen = isOpen;
        return isOpen;
	}

	@Override
	public boolean getIsOpen() {
        return this.isOpen;
	}

	@Override
	public SchemaNode getRootNode() {
        return this.CST;
	}

	@Override
	public String getCurrentContent() {
        return this.content;
	}

    @Override
    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier() {
        return new VersionedTextDocumentIdentifier(fileURI, null);
    }

	@Override
	public SchemaDocumentLexer lexer() {
        return this.lexer;
	}
}
