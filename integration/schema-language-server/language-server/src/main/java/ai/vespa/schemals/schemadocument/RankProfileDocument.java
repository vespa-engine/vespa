package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * RankProfileParser
 */
public class RankProfileDocument implements DocumentManager {
    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private SchemaDocumentScheduler scheduler;
    private SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    private String fileURI;
    private String content = null;
    private Integer version;
    private boolean isOpen = false;
    private SchemaNode CST = null;

    public RankProfileDocument(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler, String fileURI) {
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
        logger.println("CST FOR RANK PROFILE " + this.fileURI);
        if (result.CST().isPresent()) {
            this.CST = result.CST().get();
            lexer.setCST(CST);
            CSTUtils.printTree(logger, result.CST().get());
        }
    }

    public static SchemaDocument.ParseResult parseContent(ParseContext context) {
        CharSequence sequence = context.content();
        SchemaParser parserFaultTolerant = new SchemaParser(context.fileURI(), sequence);
        parserFaultTolerant.setParserTolerant(true);

        try {
            parserFaultTolerant.rankProfile(null);
        } catch(ParseException pe) {
            context.logger().println("PARSE EXCEPTION IN RANK PROFILE: " + pe.getMessage());
        } catch(IllegalArgumentException e) {
            context.logger().println("ILLEGAL ARGUMENT EXCEPTION IN RANK PROFILE: " + e.getMessage());
        }

        Node node = parserFaultTolerant.rootNode();
        var tolerantResult = SchemaDocument.parseCST(node, context);
        return tolerantResult;
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
