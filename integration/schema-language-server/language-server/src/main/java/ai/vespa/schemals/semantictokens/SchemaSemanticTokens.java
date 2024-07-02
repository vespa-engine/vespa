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

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Visitor;
import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.parser.*;

public class SchemaSemanticTokens implements Visitor {

    private static final ArrayList<Token.TokenType> keywordTokens = new ArrayList<Token.TokenType>() {{
        add(Token.TokenType.DOCUMENT);
        add(Token.TokenType.SCHEMA);
        add(Token.TokenType.FIELD);
        add(Token.TokenType.TYPE);
        add(Token.TokenType.FIELDSET); 
        add(Token.TokenType.STRUCT);
        add(Token.TokenType.RANK_PROFILE);
        add(Token.TokenType.INHERITS);
        add(Token.TokenType.FIRST_PHASE);
    }};

    private static final ArrayList<String> manualyRegisteredLSPNames = new ArrayList<String>() {{
        add("type");
    }};

    private static final Map<Token.TokenType, String> tokenTypeLSPNameMap = new HashMap<Token.TokenType, String>() {{
        put(Token.TokenType.DOUBLE, "number");
        put(Token.TokenType.INTEGER, "number");
        put(Token.TokenType.LONG, "number");
        put(Token.TokenType.DOUBLEQUOTEDSTRING, "string");
        put(Token.TokenType.SINGLEQUOTEDSTRING, "string");
        put(Token.TokenType.ARRAY, "type");
        put(Token.TokenType.WEIGHTEDSET, "type");
        put(Token.TokenType.MAP, "type");
        put(Token.TokenType.ANNOTATIONREFERENCE, "type");
        put(Token.TokenType.REFERENCE, "type");
        put(Token.TokenType.TENSOR_TYPE, "type");
    }};
    
    private static final HashMap<String, String> identifierTypeLSPNameMap = new HashMap<String, String>() {{
        put("ai.vespa.schemals.parser.ast.rootSchema", "namespace");
        put("ai.vespa.schemals.parser.ast.documentElm", "class");
        put("ai.vespa.schemals.parser.ast.fieldElm", "variable");
        put("ai.vespa.schemals.parser.ast.fieldSetElm", "variable");
        put("ai.vespa.schemals.parser.ast.fieldsElm", "variable");
        put("ai.vespa.schemals.parser.ast.structDefinitionElm", "variable");
        put("ai.vespa.schemals.parser.ast.structFieldDefinition", "variable");
        put("ai.vespa.schemals.parser.ast.rankProfile", "variable");
    }};

    private static ArrayList<String> tokenTypes;
    private static Map<Token.TokenType, Integer> tokenTypeMap;
    private static Map<String, Integer> identifierTypeMap;

    private static Integer addTokenType(String name) {
        Integer index = tokenTypes.indexOf(name);
        if (index == -1) {
            index = tokenTypes.size();
            tokenTypes.add(name);
        }
        return index;
    }

    static {
        tokenTypes = new ArrayList<String>();
        tokenTypeMap = new HashMap<Token.TokenType, Integer>();
        identifierTypeMap = new HashMap<String, Integer>();

        tokenTypes.addAll(manualyRegisteredLSPNames);

        for (Map.Entry<Token.TokenType, String> set : tokenTypeLSPNameMap.entrySet()) {
            Integer index = addTokenType(set.getValue());
            tokenTypeMap.put(set.getKey(), index);
        }

        Integer keywordIndex = addTokenType("keyword");
        for (Token.TokenType type : keywordTokens) {
            tokenTypeMap.put(type, keywordIndex);
        }

        for (Map.Entry<String, String> set : identifierTypeLSPNameMap.entrySet()) {
            Integer index = addTokenType(set.getValue());
            identifierTypeMap.put(set.getKey(), index);
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
        
        SemanticTokenMarker(Integer tokenType, SchemaNode node) {
            this.tokenType = tokenType;
            this.range = node.getRange();
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

    private static ArrayList<SemanticTokenMarker> traverseCST(SchemaNode node, PrintStream logger) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<SemanticTokenMarker>();

        Token.TokenType type = node.getType();

        if (node.isSchemaType()) {
            Integer tokenType = tokenTypes.indexOf("type");
            if (tokenType != null) {
                ret.add(new SemanticTokenMarker(tokenType, node));
            }

        } else if (type != null) {
            if (node.isUserDefinedIdentifier()) {
                SchemaNode parent = node.getParent();
                String parnetClassName = parent.getIdentifierString();
                Integer tokenType = identifierTypeMap.get(parnetClassName);
                
                if (tokenType != null) {
                    ret.add(new SemanticTokenMarker(tokenType, node));
                }

            } else {
                Integer tokenType = tokenTypeMap.get(type);
                if (tokenType != null) {
                    ret.add(new SemanticTokenMarker(tokenType, node));
                }
            }
        }

        for (int i = 0; i < node.size(); i++) {
            ArrayList<SemanticTokenMarker> markers = traverseCST(node.get(i), logger);
            ret.addAll(markers);
        }

        return ret;
    }

    public static SemanticTokens getSemanticTokens(EventContext context) {

        SchemaNode node = context.document.getRootNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        ArrayList<SemanticTokenMarker> markers = traverseCST(node, context.logger);
        ArrayList<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(markers);

        return new SemanticTokens(compactMarkers);
    }
}
