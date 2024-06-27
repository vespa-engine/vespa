package ai.vespa.schemals.context;

import java.io.PrintStream;

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

    private void parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        SchemaParser parser = new SchemaParser(sequence);
        parser.setParserTolerant(false);

        try {

            ParsedSchema root = parser.Root();
            diagnosticsHandler.clearDiagnostics(fileURI);
            faultySchema = false;
            
            logger.println("VALID");
        } catch (ParseException e) {
            faultySchema = true;

            Node.TerminalNode node = e.getToken();


            // Position beginPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn() - 1);
            // Position endPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn() + 1);

            // Range range = new Range(beginPosition, endPosition);
            Range range = CSTUtils.getNodeRange(node);

            diagnosticsHandler.publishDiagnostics(fileURI, range, e.getMessage());
        }

        parser.setParserTolerant(true);

        Node node = parser.rootNode();
        buildCST(node);

    }

    private void buildCST(Node node) {
        CST = node;
        logger.println("Trying to print tree");
        CSTUtils.printTree(logger, node);
        // logger.println(node.getType());
        // //logger.println(node.getSource());
        // logger.println(node.getBeginLine() + " | " + node.getBeginColumn());
        // logger.println(node.getBeginOffset() + " : " + node.getEndOffset());
        // Range range = getNodeRange(node);
        // logger.println(range);

        // if (node.hasChildNodes()) {
        //     for (int i = 0; i < node.size(); i++) {
        //         buildCST(node.get(i));
        //     }
        // }
    }

    public boolean isFaulty() {
        return faultySchema;
    }

    public Node getRootNode() {
        return CST;
    }
}
