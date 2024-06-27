package ai.vespa.schemals.context;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.parser.*;

import ai.vespa.schemals.tree.CSTUtils;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;

    private String fileURI = "";
    private String content = "";
    
    private Node CST;
    private boolean faultySchema = true;

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI) {
        this(logger, diagnosticsHandler, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.fileURI = fileURI;
        if (content != null) {
            this.content = content;
            parseContent();
        };
    }

    public void updateFileContent(String content) {
        this.content = content;
        parseContent();
    }

    public String getFileURI() {
        return fileURI;
    }

    public String getFileName() {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
    }

    private void parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        SchemaParser parserStrict = new SchemaParser(getFileName(), sequence);
        parserStrict.setParserTolerant(false);

        ArrayList<Diagnostic> errors = new ArrayList<Diagnostic>();

        try {

            ParsedSchema root = parserStrict.Root();
            faultySchema = false;
            
            logger.println("VALID");
        } catch (ParseException e) {
            faultySchema = true;

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);

            errors.add(new Diagnostic(range, e.getMessage()));
        }

        SchemaParser parserFaultTolerant = new SchemaParser(getFileName(), sequence);
        try {
            parserFaultTolerant.Root();
        } catch (ParseException e) {
            // Ignore
        }

        Node node = parserFaultTolerant.rootNode();
        buildCST(node);

        errors.addAll(findDirtyNode(node));

        diagnosticsHandler.publishDiagnostics(fileURI, errors);

    }

    private void buildCST(Node node) {
        CST = node;
        logger.println("Trying to print tree");
        CSTUtils.printTree(logger, node);
    }

    private ArrayList<Diagnostic> findDirtyNode(Node node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        for (Node child : node) {
            ret.addAll(findDirtyNode(child));
        }

        if (node.isDirty() && ret.size() == 0) {
            Range range = CSTUtils.getNodeRange(node);

            ret.add(new Diagnostic(range, "Dirty Node"));
        }


        return ret;
    }

    public boolean isFaulty() {
        return faultySchema;
    }

    public Node getRootNode() {
        return CST;
    }
}
