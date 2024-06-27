package ai.vespa.schemals.context;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.parser.*;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;

    private String fileURI = "";
    private String content = "";
    
    private SchemaNode CST;
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
        createCST(node);

        errors.addAll(findDirtyNode(node));

        diagnosticsHandler.publishDiagnostics(fileURI, errors);

    }

    private static final HashMap<Token.TokenType, String> tokenParentClassPairs = new HashMap<Token.TokenType, String>() {{
        put(Token.TokenType.SCHEMA, "ai.vespa.schemals.parser.ast.rootSchema");
        put(Token.TokenType.DOCUMENT, "ai.vespa.schemals.parser.ast.documentElm");
        put(Token.TokenType.FIELD, "ai.vespa.schemals.parser.ast.fieldElm");
        put(Token.TokenType.FIELDSET, "ai.vespa.schemals.parser.ast.fieldSetElm");
    }};

    private void improveCST(SchemaNode node) {
        
        SchemaNode parent = node.getParent();
        Node.NodeType nodeType = node.getType();
        String parentCNComp = tokenParentClassPairs.get(nodeType);
        if (
            parent != null &&
            parent.get(0) == node &&
            parent.getIdentifierString() == parentCNComp &&
            parent.get(1) != null
        ) {
            SchemaNode child = parent.get(1);
            child.setUserDefinedIdentifier();
        }

        for (int i = 0; i < node.size(); i++) {
            improveCST(node.get(i));
        }
    }

    private void createCST(Node node) {
        CST = new SchemaNode(node);
        improveCST(CST);
        CSTUtils.printTree(logger, CST);
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

    public SchemaNode getRootNode() {
        return CST;
    }
}
