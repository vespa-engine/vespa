package ai.vespa.schemals.parser;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.parser.*;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;

    private String fileURI = "";
    private String content = "";

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI) {
        this(logger, diagnosticsHandler, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.fileURI = fileURI;
        if (content != null) this.content = content;
    }

    public void updateFileContent(String content) {
        this.content = content;
        parseContent();
    }

    public String getFileURI() {
        return fileURI;
    }

    private void publishDiagnostics(Range range, String message) {
        diagnosticsHandler.publishDiagnostics(fileURI, range, message);
    }

    private void parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        SchemaParser parser = new SchemaParser(sequence);

        for (int i = 0; i < 5; i++) {
            logger.println(parser.lastConsumedToken.isDirty() + " | " + parser.lastConsumedToken.getSource());

            try {
    
                ParsedSchema root = parser.Root();
                
                logger.println(root);
                i = 4;
            } catch (ParseException e) {
                logger.println(e);
                Node.TerminalNode node = e.getToken();
                logger.println(node.getBeginOffset());


                Position beginPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn() - 3);
                Position endPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn());

                Range range = new Range(beginPosition, endPosition);

                publishDiagnostics(range, "Parser error");

                //parser.lastConsumedToken = parser.lastConsumedToken.getNext();

            }
        }
    }
}
