package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.yahoo.searchlib.rankingexpression.rule.CompositeNode;
import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.EXPRESSION_SL;
import ai.vespa.schemals.parser.ast.IDENTIFIER_WITH_DASH;
import ai.vespa.schemals.parser.ast.MATCHFEATURES_SL;
import ai.vespa.schemals.parser.ast.RANKFEATURES_SL;
import ai.vespa.schemals.parser.ast.SUMMARYFEATURES_SL;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;

/**
 * SchemaRankExpressionParser is a parser for rank epxressions, which is small part in other files
 */
public class SchemaRankExpressionParser {

    private static final HashSet<TokenType> multilineTokens = new HashSet<>() {{
        add(TokenType.EXPRESSION_ML);
        add(TokenType.SUMMARYFEATURES_ML);
        add(TokenType.SUMMARYFEATURES_ML_INHERITS);
        add(TokenType.MATCHFEATURES_ML);
        add(TokenType.MATCHFEATURES_ML_INHERITS);
        add(TokenType.RANKFEATURES_ML);
    }};

    private static final HashSet<TokenType> inheritsTokens = new HashSet<>() {{
        add(TokenType.SUMMARYFEATURES_ML_INHERITS);
        add(TokenType.MATCHFEATURES_ML_INHERITS);
    }};

    // This has to match the tokens in the ccc file
    private static final HashMap<TokenType, String> preTextMap = new HashMap<TokenType, String>() {{
        put(TokenType.EXPRESSION_SL, "expression");
        put(TokenType.EXPRESSION_ML, "expression");
        put(TokenType.SUMMARYFEATURES_SL, "summary-features");
        put(TokenType.SUMMARYFEATURES_ML, "summary-features");
        put(TokenType.SUMMARYFEATURES_ML_INHERITS, "summary-features inherits");
        put(TokenType.MATCHFEATURES_SL, "match-features");
        put(TokenType.MATCHFEATURES_ML, "match-features");
        put(TokenType.MATCHFEATURES_ML_INHERITS, "match-features inherits");
        put(TokenType.RANKFEATURES_SL, "rank-features");
        put(TokenType.RANKFEATURES_ML, "rank-features");
    }};

    private static final HashMap<TokenType, TokenType> simplifyTokenTypeMap = new HashMap<TokenType, TokenType>() {{
        put(TokenType.EXPRESSION_SL, TokenType.EXPRESSION_SL);
        put(TokenType.EXPRESSION_ML, TokenType.EXPRESSION_SL);
        put(TokenType.SUMMARYFEATURES_SL, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.SUMMARYFEATURES_ML, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.SUMMARYFEATURES_ML_INHERITS, TokenType.SUMMARYFEATURES_SL);
        put(TokenType.MATCHFEATURES_SL, TokenType.MATCHFEATURES_SL);
        put(TokenType.MATCHFEATURES_ML, TokenType.MATCHFEATURES_SL);
        put(TokenType.MATCHFEATURES_ML_INHERITS, TokenType.MATCHFEATURES_SL);
        put(TokenType.RANKFEATURES_SL, TokenType.RANKFEATURES_SL);
        put(TokenType.RANKFEATURES_ML, TokenType.RANKFEATURES_SL);
    }};

    private static final Map<TokenType, Class<? extends Node>> tokenTypeToASTClass = new HashMap<>() {{
        put(TokenType.EXPRESSION_SL, EXPRESSION_SL.class);
        put(TokenType.SUMMARYFEATURES_SL, SUMMARYFEATURES_SL.class);
        put(TokenType.MATCHFEATURES_SL, MATCHFEATURES_SL.class);
        put(TokenType.RANKFEATURES_SL, RANKFEATURES_SL.class);
    }};

    private static record ExpressionMetaData(
        boolean multiline,
        boolean inherits,
        Position expressionOffset,
        String preText
    ) {}

    private static ExpressionMetaData findExpressionMetaData(SchemaNode node) {

        TokenType nodeType = node.findFirstLeaf().getSchemaNode().getSchemaType();

        boolean inherits = inheritsTokens.contains(nodeType);

        boolean isMultiline = multilineTokens.contains(nodeType);

        char splitChar = isMultiline ? '{' : ':';

        int offset = node.getText().indexOf(splitChar) + 1;

        String preString = node.getText().substring(0, offset);

        int numberOfNewLines = (int)preString.chars().filter(ch -> ch == '\n').count();

        offset = preString.length() - preString.lastIndexOf('\n') - 1;

        Position expressionOffset = new Position(
            numberOfNewLines,
            offset
        );

        return new ExpressionMetaData(
            isMultiline,
            inherits,
            expressionOffset,
            preString
        );
    }

    static void printExpressionTree(PrintStream logger, ExpressionNode node, int indent) {
        String[] classList = node.getClass().toString().split("[.]");
        String className = classList[classList.length - 1];
        String msg = node.toString() + " (" + className + ")";

        if (node instanceof ReferenceNode) {
            var ref = ((ReferenceNode)node).reference();

            msg += " [REF: " + ref.name() + "]";

            if (ref.isIdentifier()) {
                msg += " [IDENTIFIER]";
            } 
            if (ref.output() != null) {
                msg += " [OUTPUT: " + ref.output() + "]";
            }
        }
        logger.println(new String(new char[indent]).replaceAll("\0", "\t") + msg);
        if (node instanceof CompositeNode) {
            for (var child : ((CompositeNode)node).children()) {
                printExpressionTree(logger, child, indent + 1);
            }
        }
    }

    static SchemaNode parseRankingExpression(ParseContext context, SchemaNode node, Position expressionOffset, List<Diagnostic> diagnostics) {
        String expressionString = node.getRankExpressionString();

        Position expressionStart = CSTUtils.addPositions(node.getRange().getStart(), expressionOffset);
        
        if (expressionString == null)return null;
        CharSequence sequence = expressionString;

        RankingExpressionParser tolerantParser = new RankingExpressionParser(context.fileURI(), sequence);
        tolerantParser.setParserTolerant(true);

        try {
            if (node.containsExpressionData()) {
                tolerantParser.expression();
            } else {
                tolerantParser.featureList();
            }
        } catch (ai.vespa.schemals.parser.rankingexpression.ParseException pe) {
            // Ignore
        } catch (IllegalArgumentException ex) {
            // Ignore
        }

        if (tolerantParser.rootNode() == null) return null;
        return new SchemaNode(tolerantParser.rootNode(), expressionStart);
    }

    private static SchemaNode tokenFromRawText(ParseContext context, SchemaNode node, TokenType type, String text, Range nodeRange) {

        SchemaNode ret = new SchemaNode(
            nodeRange,
            text,
            type.toString().toUpperCase() + " [CUSTOM LANGUAGE]"
        );

        ret.setSchemaType(type);

        if (tokenTypeToASTClass.containsKey(type)) {
            ret.setSimulatedASTClass(tokenTypeToASTClass.get(type));
        }

        return ret;
    }

    private static final HashSet<Character> skipCharacters = new HashSet<>() {{
        add(' ');
        add('\t');
        add('\r');
        add('\f');
    }};

    private static SchemaNode parseIdentifierWithDash(ParseContext context, SchemaNode node, String text, int startSearch, int endSearch) {
        String subString = text.substring(startSearch, endSearch);
        int leadingSplitPos = 0;
        while (
            leadingSplitPos < subString.length() - 1 &&
            skipCharacters.contains(subString.charAt(leadingSplitPos))
        ) {
            leadingSplitPos++;
        }

        int trailingSplitPos = subString.length() - 1;
        while (
            trailingSplitPos > 0 &&
            skipCharacters.contains(subString.charAt(trailingSplitPos - 1))
        ) {
            trailingSplitPos--;
        }

        // if no whitespace after identifier
        if (!skipCharacters.contains(subString.charAt(trailingSplitPos)))++trailingSplitPos;

        subString = subString.substring(leadingSplitPos, trailingSplitPos);

        int line = node.getRange().getStart().getLine();
        int character = node.getRange().getStart().getCharacter();
        Range range = new Range(
            new Position(line, character + startSearch + leadingSplitPos),
            new Position(line, character + startSearch + trailingSplitPos)
        );

        String identifierString = TokenType.IDENTIFIER_WITH_DASH.toString().toUpperCase() + " [CUSTOM LANGUAGE]";

        SchemaNode child = new SchemaNode(range, subString, identifierString);
        SchemaNode parent = new SchemaNode(range, subString, "identifierWithDashStr [CUSTOM LANGUAGE]");
        
        child.setSimulatedASTClass(IDENTIFIER_WITH_DASH.class);
        parent.setSimulatedASTClass(identifierWithDashStr.class);

        parent.addChild(child);
        return parent;
    }

    private static ArrayList<SchemaNode> findPreChildren(ParseContext context, ExpressionMetaData metaData, SchemaNode node) {
        ArrayList<SchemaNode> children = new ArrayList<>();

        TokenType nodeType = node.findFirstLeaf().getSchemaNode().getSchemaType();
        String firstTokenString = preTextMap.get(nodeType);
        if (firstTokenString == null) {
            return null;
        }


        TokenType simplifiedType = simplifyTokenTypeMap.get(nodeType);
        if (simplifiedType == null) return null;

        // Find the last token
        char searchChar   = metaData.multiline() ? '{' : ':';
        TokenType charTokenType = metaData.multiline() ? TokenType.LBRACE : TokenType.COLON;
        int charPosition = metaData.preText().indexOf(searchChar, firstTokenString.length());

        if (!metaData.inherits()) {
            Position startPosition = node.getRange().getStart();
            Position endPosition = new Position(startPosition.getLine(), startPosition.getCharacter() + firstTokenString.length());
            children.add(tokenFromRawText(context, node, simplifiedType, firstTokenString, new Range(startPosition, endPosition)));

        } else {
            int spacePos = firstTokenString.indexOf(' ');
            Position startPosition = node.getRange().getStart();
            Position endPosition = new Position(startPosition.getLine(), startPosition.getCharacter() + spacePos);
            children.add(tokenFromRawText(
                context,
                node,
                simplifiedType,
                firstTokenString.substring(0, spacePos),
                new Range(startPosition, endPosition)
            ));
            children.add(tokenFromRawText(
                context,
                node,
                TokenType.INHERITS,
                firstTokenString.substring(spacePos + 1, firstTokenString.length()),
                new Range(
                    new Position(endPosition.getLine(), endPosition.getCharacter() + 1), 
                    new Position(endPosition.getLine(), endPosition.getCharacter() + 1 + "inherits".length())
                )
            ));

            // We know at between the inherits and the charPosition it will be an IdentifierWithDashStr
            SchemaNode inheritsIdentifierNode = parseIdentifierWithDash(context, node, metaData.preText(), firstTokenString.length(), charPosition);
            Optional<Symbol> scope = CSTUtils.findScope(node);
            if (scope.isPresent()) {
                inheritsIdentifierNode.setSymbol(SymbolType.RANK_PROFILE, context.fileURI(), scope.get());
            } else {
                inheritsIdentifierNode.setSymbol(SymbolType.RANK_PROFILE, context.fileURI());
            }
            inheritsIdentifierNode.setSymbolStatus(SymbolStatus.UNRESOLVED);
            children.add(inheritsIdentifierNode);
        }


        Position startPos = CSTUtils.addPositions(node.getRange().getStart(), metaData.expressionOffset());
        Position endPos = new Position(startPos.getLine(), startPos.getCharacter() + 1);

        children.add(tokenFromRawText(context, node, charTokenType, "" + searchChar, new Range(startPos, endPos)));

        return children;
    }

    static void embedCST(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) return;


        ExpressionMetaData metaData = findExpressionMetaData(node);

        List<SchemaNode> newChildren = findPreChildren(context, metaData, node);
        if (newChildren == null) return;

        SchemaNode rankExpressionNode = parseRankingExpression(context, node, metaData.expressionOffset(), diagnostics);

        if (rankExpressionNode != null) {
            newChildren.add(rankExpressionNode);
        }

        if (metaData.multiline()) {
            Position startPos = new Position(node.getRange().getEnd().getLine(), node.getRange().getEnd().getCharacter() - 1);
            Position endPos = node.getRange().getEnd();
            Range range = new Range(startPos, endPos);
            newChildren.add(tokenFromRawText(context, node, TokenType.RBRACE, "}", range));
        }

        node.clearChildren();
        node.addChildren(newChildren);
    }
}
