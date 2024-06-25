package ai.vespa.schemals.parser;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.ParseException;

public class ParserWrapper {

    private PrintStream logger;

    private boolean faulty = false;
    private Node faultyNode;

    public ParserWrapper(PrintStream logger) {
        this.logger = logger;
    }

    public void debug(String input) {

        CharSequence sequence = input;

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

                //parser.lastConsumedToken = parser.lastConsumedToken.getNext();

            }
        }

        logger.println("Hade bra!");

    }

    private Range getNodeRange(Node node) {

        TokenSource token = node.getTokenSource();

        Position beginPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn() - 3);
        Position endPosition = new Position(node.getBeginLine() - 1, node.getBeginColumn());

        return new Range(beginPosition, endPosition);
    }

    public void parse(String input) {
        CharSequence sequence = input;

        SchemaParser parser = new SchemaParser(sequence);

        try {

            ParsedSchema root = parser.Root();

            faulty = false;
            faultyNode = null;
            logger.println("VALID");
        } catch (ParseException e) {
            logger.println(e);
            Node.TerminalNode node = e.getToken();
            faultyNode = node;
            faulty = true;

        }
    }

    public boolean getFaulty() {
        return faulty;
    }

    public Range getFaultyRange() {
        return getNodeRange(faultyNode);
    }



}