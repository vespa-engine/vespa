package ai.vespa.schemals.semantictokens;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;

import ai.vespa.schemals.parser.SchemaDocumentParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.parser.*;

public class SemanticTokensUtils {

    private static final Map<Token.TokenType, String> tokenTypeLSPnameMap = new HashMap<Token.TokenType, String>() {{
        put(Token.TokenType.DOCUMENT, "class");
        put(Token.TokenType.SCHEMA, "namespace");
        put(Token.TokenType.FUNCTION, "function");
        put(Token.TokenType.TYPE, "type");
        put(Token.TokenType.DOUBLE, "number");
        put(Token.TokenType.INTEGER, "number");
        put(Token.TokenType.LONG, "number");
    }};

    private static ArrayList<String> tokenTypes;
    private static Map<Token.TokenType, Integer> tokenTypeMap;


    static {
        tokenTypeMap = new HashMap<Token.TokenType, Integer>();
        tokenTypes = new ArrayList<String>();

        int i = 0;
        for (Map.Entry<Token.TokenType, String> set : tokenTypeLSPnameMap.entrySet()) {
            Integer index = tokenTypes.indexOf(set.getValue());
            if (index == -1) {
                tokenTypes.add(set.getValue());
                index = i;
                i++;
            }
            tokenTypeMap.put(set.getKey(), index);
        }
    }


    private static final ArrayList<String> tokenModifiers = new ArrayList<String>();

    public static SemanticTokensWithRegistrationOptions getSemanticTokensRegistrationOptions() {
        return new SemanticTokensWithRegistrationOptions(
            new SemanticTokensLegend(tokenTypes, tokenModifiers),
            new SemanticTokensServerFull(false)
        );
    }

    private static class SemanticTokenMarker {
        private static final int LINE_INDEX = 0;
        private static final int COLUMN_INDEX = 1;

        private int tokenType;
        private Range range;

        SemanticTokenMarker(Node.NodeType tokenType, Range range) {
            this.tokenType = tokenTypeMap.get(tokenType);
            this.range = range;
        }

        private ArrayList<Integer> compactForm() {
            int length = range.getEnd().getCharacter() - range.getStart().getCharacter();

            return new ArrayList<Integer>() {{
                add(range.getStart().getLine());
                add(range.getStart().getCharacter());
                add(length);
                add(tokenType);
                add(0);
            }};
        }

        static ArrayList<Integer> concatCompactForm(ArrayList<SemanticTokenMarker> markers) {
            ArrayList<Integer> ret = new ArrayList<Integer>(markers.size() * 5);

            if (markers.size() == 0) {
                return ret;
            }

            ret.addAll(markers.get(0).compactForm());

            for (int i = 1; i < markers.size(); i++) {
                ArrayList<Integer> markerCompact = markers.get(i).compactForm();
                ArrayList<Integer> lastMarkerCompact = markers.get(i - 1).compactForm();
                markerCompact.set(LINE_INDEX, markerCompact.get(LINE_INDEX) - lastMarkerCompact.get(LINE_INDEX));
                if (markerCompact.get(LINE_INDEX) == 0) {
                    markerCompact.set(COLUMN_INDEX, markerCompact.get(COLUMN_INDEX) - lastMarkerCompact.get(COLUMN_INDEX));
                }
                ret.addAll(markerCompact);
            }

            return ret;
        }
    }

    private static ArrayList<SemanticTokenMarker> traverseNode(Node node, PrintStream logger) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<SemanticTokenMarker>();

        Node.NodeType type = node.getType();
        if (type != null && tokenTypeMap.containsKey(type)) {
            logger.println(type);
            Range range = CSTUtils.getNodeRange(node);
            ret.add(new SemanticTokenMarker(type, range));
        }

        if (node.hasChildNodes()) {
            for (int i = 0; i < node.size(); i++) {
                ArrayList<SemanticTokenMarker> markers = traverseNode(node.get(i), logger);
                ret.addAll(markers);
            }
        }

        return ret;
    }

    public static SemanticTokens getSemanticTokens(SchemaDocumentParser document, PrintStream logger) {

        Node node = document.getRootNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        ArrayList<SemanticTokenMarker> markers = traverseNode(node, logger);
        ArrayList<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(markers);

        logger.println(compactMarkers);

        return new SemanticTokens(compactMarkers);
    }
}
