package ai.vespa.schemals.semantictokens;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensServerFull;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Visitor;
import ai.vespa.schemals.context.EventContext;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.TokenType;

public class SchemaSemanticTokens implements Visitor {

    private static final ArrayList<TokenType> keywordTokens = new ArrayList<TokenType>() {{
        add(TokenType.DOCUMENT);
        add(TokenType.FIELD);
        add(TokenType.FIELDSET); 
        add(TokenType.FIRST_PHASE);
        add(TokenType.INHERITS);
        add(TokenType.RANK_PROFILE);
        add(TokenType.SCHEMA);
        add(TokenType.STRUCT);
        add(TokenType.STRUCT_FIELD);
        add(TokenType.TYPE);
    }};

    private static final ArrayList<String> manualyRegisteredLSPNames = new ArrayList<String>() {{
        add("type");
        add("comment");
    }};

    private static final Map<TokenType, String> tokenTypeLSPNameMap = new HashMap<TokenType, String>() {{
        put(TokenType.DOUBLE, "number");
        put(TokenType.INTEGER, "number");
        put(TokenType.LONG, "number");
        put(TokenType.DOUBLEQUOTEDSTRING, "string");
        put(TokenType.SINGLEQUOTEDSTRING, "string");
        put(TokenType.ARRAY, "type");
        put(TokenType.WEIGHTEDSET, "type");
        put(TokenType.MAP, "type");
        put(TokenType.ANNOTATIONREFERENCE, "type");
        put(TokenType.REFERENCE, "type");
        put(TokenType.TENSOR_TYPE, "type");
    }};
    
    private static final HashMap<String, String> identifierTypeLSPNameMap = new HashMap<String, String>() {{
        put("ai.vespa.schemals.parser.ast.rootSchema", "namespace");
        put("ai.vespa.schemals.parser.ast.documentElm", "class");
        put("ai.vespa.schemals.parser.ast.fieldElm", "variable");
        put("ai.vespa.schemals.parser.ast.fieldSetElm", "variable");
        put("ai.vespa.schemals.parser.ast.fieldsElm", "variable");
        put("ai.vespa.schemals.parser.ast.structFieldElm", "variable");
        put("ai.vespa.schemals.parser.ast.structDefinitionElm", "variable");
        put("ai.vespa.schemals.parser.ast.structFieldDefinition", "variable");
        put("ai.vespa.schemals.parser.ast.rankProfile", "variable");
    }};

    private static ArrayList<String> tokenTypes;
    private static Map<TokenType, Integer> tokenTypeMap;
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
        tokenTypeMap = new HashMap<TokenType, Integer>();
        identifierTypeMap = new HashMap<String, Integer>();

        tokenTypes.addAll(manualyRegisteredLSPNames);

        for (Map.Entry<TokenType, String> set : tokenTypeLSPNameMap.entrySet()) {
            Integer index = addTokenType(set.getValue());
            tokenTypeMap.put(set.getKey(), index);
        }

        Integer keywordIndex = addTokenType("keyword");
        for (TokenType type : keywordTokens) {
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
            this(tokenType, node.getRange());
        }
        
        SemanticTokenMarker(Integer tokenType, Range range) {
            this.tokenType = tokenType;
            this.range = range;
        }

        Range getRange() { return range; }

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

        TokenType type = node.getType();

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

    private static ArrayList<SemanticTokenMarker> convertCommentRanges(ArrayList<Range> comments) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<>();

        int tokenType = tokenTypes.indexOf("comment");

        for (Range range : comments) {
            ret.add(new SemanticTokenMarker(tokenType, range));
        }

        return ret;
    }

    private static ArrayList<SemanticTokenMarker> findComments(SchemaNode rootNode) {
        TokenSource tokenSource = rootNode.getTokenSource();
        String source = tokenSource.toString();
        ArrayList<Range> ret = new ArrayList<>();

        int index = source.indexOf("#");
        while (index >= 0) {
            Position start = CSTUtils.getPositionFromOffset(tokenSource, index);

            index = source.indexOf("\n", index + 1);

            if (index < 0) {
                index = source.length() - 1;
            }

            Position end = CSTUtils.getPositionFromOffset(tokenSource, index);

            ret.add(new Range(start, end));

            index = source.indexOf("#", index + 1);
        }

        return convertCommentRanges(ret);
    }

    // This function assumes that both of the lists are sorted, and that no elements are overlapping
    private static ArrayList<SemanticTokenMarker> mergeSemanticTokenMarkers(ArrayList<SemanticTokenMarker> lhs, ArrayList<SemanticTokenMarker> rhs) {
        ArrayList<SemanticTokenMarker> ret = new ArrayList<>(lhs.size() + rhs.size());

        int lhsIndex = 0;
        int rhsIndex = 0;
        while (
            lhsIndex < lhs.size() &&
            rhsIndex < rhs.size()
        ) {
            Position rhsPos = rhs.get(rhsIndex).getRange().getStart();
            Position lhsPos = lhs.get(lhsIndex).getRange().getStart();

            if (CSTUtils.positionLT(lhsPos, rhsPos)) {
                ret.add(lhs.get(lhsIndex));
                lhsIndex++;
            } else {
                ret.add(rhs.get(rhsIndex));
                rhsIndex++;
            }
        }

        for (int i = lhsIndex; i < lhs.size(); i++) {
            ret.add(lhs.get(i));
        }

        for (int i = rhsIndex; i < rhs.size(); i++) {
            ret.add(rhs.get(i));
        }

        return ret;
    }

    public static SemanticTokens getSemanticTokens(EventContext context) {

        SchemaNode node = context.document.getRootNode();
        if (node == null) {
            return new SemanticTokens(new ArrayList<>());
        }

        ArrayList<SemanticTokenMarker> comments = findComments(node);

        ArrayList<SemanticTokenMarker> markers = traverseCST(node, context.logger);
        ArrayList<Integer> compactMarkers = SemanticTokenMarker.concatCompactForm(
            mergeSemanticTokenMarkers(markers, comments)
        );

        return new SemanticTokens(compactMarkers);
    }
}
