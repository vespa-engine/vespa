package ai.vespa.schemals.tree;

import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.yahoo.searchlib.rankingexpression.rule.ExpressionNode;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.rankingexpression.ast.BaseNode;
import ai.vespa.schemals.parser.rankingexpression.ast.lambdaFunction;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class CSTUtils {

    public static Position getPositionFromOffset(TokenSource tokenSource, int offset) {
        int line = tokenSource.getLineFromOffset(offset) - 1;
        int startOfLineOffset = tokenSource.getLineStartOffset(line + 1);
        int column = offset - startOfLineOffset;
        return new Position(line, column);
    }

    public static Range getRangeFromOffsets(TokenSource tokenSource, int beginOffset, int endOffset) {
        Position begin = getPositionFromOffset(tokenSource, beginOffset);
        Position end = getPositionFromOffset(tokenSource, endOffset);
        return new Range(begin, end);
    }

    public static Range getNodeRange(ai.vespa.schemals.parser.Node node) {
        TokenSource tokenSource = node.getTokenSource();
        try {
            return getRangeFromOffsets(tokenSource, node.getBeginOffset(), node.getEndOffset());
        } catch(Exception e) {
            // TODO: something happens when offsets are bad
        }        
        return new Range(new Position(0, 0), new Position(0, 0));
    }


    public static boolean positionLT(Position lhs, Position rhs) {
        return (
            lhs.getLine() < rhs.getLine() || (
                lhs.getLine() == rhs.getLine() && 
                lhs.getCharacter() < rhs.getCharacter()
            )
        );
    }

    public static boolean positionInRange(Range range, Position pos) {
        return (
            positionLT(pos, range.getEnd()) && (
                positionLT(range.getStart(), pos) ||
                range.getStart().equals(pos)
            )
        );
    }

    public static Position addPositions(Position lhs, Position rhs) {
        int line = lhs.getLine() + rhs.getLine();
        int column = rhs.getCharacter();
        if (rhs.getLine() == 0) {
            column += lhs.getCharacter();
        }

        return new Position(line, column);
    }

    public static Range unionRanges(Range a, Range b) {
        Position newStart = positionLT(a.getStart(), b.getStart()) ? a.getStart() : b.getStart(); // the smaller of the two
        Position newEnd   = positionLT(a.getEnd(), b.getEnd()) ? b.getEnd() : a.getEnd(); // the larger of the two
        return new Range(newStart, newEnd);
    }

    public static Range addPositionToRange(Position lhs, Range rhs) {
        return new Range(
            addPositions(lhs, rhs.getStart()),
            addPositions(lhs, rhs.getEnd())
        );
    }

    /* Returns the last non-dirty node before the supplied position */
    public static Node getLastCleanNode(Node node, Position pos) {
        if (node == null) return null;
        for (int i = node.size() - 1; i >= 0; i--) {
            Node result = getLastCleanNode(node.get(i), pos);
            if (result != null) return result;
        }

        Range range = node.getRange();
        if (!positionLT(pos, range.getStart()) && !node.getIsDirty()) {
            return node;
        }

        return null;
    }

    public static Node getNodeAtOrBeforePosition(Node CST, Position pos) {
        return getNodeAtPosition(CST, pos, false, true);
    }

    public static Node getLeafNodeAtPosition(Node CST, Position pos) {
        return getNodeAtPosition(CST, pos, true, false);
    }

    public static Node getNodeAtPosition(Node CST, Position pos) {
        return getNodeAtPosition(CST, pos, false, false);
    }

    public static Node getSymbolAtPosition(Node CST, Position pos) {
        Node node = getNodeAtPosition(CST, pos);

        while (node != null && !node.hasSymbol()) {
            node = node.getParent();
        }

        return node;
    }

    /*
     * Helper method for walking up the tree to find some AST class
     */
    public static Node findASTClassAncestor(Node node, Class<?> astClass) {
        while (node != null) {
            if (node.getASTClass() == astClass) return node;
            node = node.getParent();
        }
        return null;
    }

    private static Node getNodeAtPosition(Node node, Position pos, boolean onlyLeaf, boolean findNearest) {
        if (node == null) return null;

        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        if (!CSTUtils.positionInRange(node.getRange(), pos)) {
            if (findNearest && !onlyLeaf)return node;
            return null;
        }

        for (Node child : node) {
            if (CSTUtils.positionInRange(child.getRange(), pos)) {
                return getNodeAtPosition(child, pos, onlyLeaf, findNearest);
            }
        }

        if (onlyLeaf)return null;

        return node;
    }

    public static Optional<Symbol> findScope(Node node) {
        Node currentNode = node;

        while (
            currentNode != null
        ) {
            Class<?> astClass = currentNode.getASTClass();

            if (astClass != null && (
                SchemaIndex.IDENTIFIER_TYPE_MAP.containsKey(astClass) ||
                SchemaIndex.IDENTIFIER_WITH_DASH_TYPE_MAP.containsKey(astClass) ||
                astClass.equals(lambdaFunction.class)
            ) && !astClass.equals(documentElm.class) // edge case for not setting document as scope when there is a schema
            ) {

                // Find the symbol definition
                // TODO: Refactor in a more general way
                int indexGuess = 1;

                if (currentNode.getASTClass() == functionElm.class) {
                    indexGuess = 2;
                }

                if (currentNode.getASTClass() == lambdaFunction.class) {
                    indexGuess = 0;
                }

                if (indexGuess < currentNode.size()) {
                    Node potentialDefinition = currentNode.get(indexGuess);
                    if (potentialDefinition.hasSymbol() && potentialDefinition.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
                        return Optional.of(potentialDefinition.getSymbol());
                    }
                }
            }

            currentNode = currentNode.getParent();
        }

        return Optional.empty();
    }

    /*
     * Logger utils
     * */

    private static final String SPACER = " ";

    public static void printTree(ClientLogger logger, ai.vespa.schemals.parser.Node node) {
        printTree(logger, node, 0);
    }

    public static void printTree(ClientLogger logger, ai.vespa.schemals.parser.Node node, Integer indent) {
        if (node == null) return;
        Range range = getNodeRange(node);
        logger.info(new String(new char[indent]).replace("\0", SPACER) + node.getClass().getName()
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
        );

        for (ai.vespa.schemals.parser.Node child : node) {
            printTree(logger, child, indent + 1);
        }
    }

    public static void printTree(ClientLogger logger, SchemaNode node) {
        printTree(logger, node, 0);
    }

    public static void printTree(ClientLogger logger, SchemaNode node, Integer indent) {
        if (node == null) return;

        logger.info(new String(new char[indent]).replace("\0", SPACER) + schemaNodeString(node));

        for (Node child : node) {
            if (!child.isSchemaNode()) {
                throw new UnsupportedOperationException("Was only expecting to receive SchemaNodes");
            }
            printTree(logger, child.getSchemaNode(), indent + 1);
        }

        if (node.hasIndexingNode()) {
            ILUtils.printTree(logger, node.getOriginalIndexingNode(), indent + 1);
        }

        if (node.hasRankExpressionNode()) {
            RankingExpressionUtils.printTree(logger, node.getOriginalRankExpressionNode(), indent + 1);
        }
    }

    public static void printTreeUpToPosition(ClientLogger logger, ai.vespa.schemals.parser.Node node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(ClientLogger logger, SchemaNode node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(ClientLogger logger, ai.vespa.schemals.parser.Node node, Position pos, Integer indent) {
        Range range = getNodeRange(node);

        if (!positionLT(pos, range.getStart())) {
            boolean dirty = node.isDirty();
            logger.info(new String(new char[indent]).replace("\0", SPACER) + node.getClass().getName() + (dirty ? " [DIRTY]" : "")
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
                    );
        }

        for (ai.vespa.schemals.parser.Node child : node) {
            printTreeUpToPosition(logger, child, pos, indent + 1);
        }
    }

    public static void printTreeUpToPosition(ClientLogger logger, SchemaNode node, Position pos, Integer indent) {

        Range range = node.getRange();
        if (!positionLT(pos, range.getStart())) {
            node.getIsDirty();
            logger.info(new String(new char[indent]).replace("\0", SPACER) + schemaNodeString(node));
        }


        for (Node child : node) {
            if (!child.isSchemaNode()) {
                throw new UnsupportedOperationException("Only expected SchemaNodes");
            }
            printTreeUpToPosition(logger, child.getSchemaNode(), pos, indent + 1);
        }
    }

    private static String schemaNodeString(SchemaNode node) {

        String ret = node.getClassLeafIdentifierString();

        if (node.getIsDirty()) {
            ret += " [DIRTY]";
        }

        if (node.containsOtherLanguageData(LanguageType.INDEXING)) {
            ret += " [ILSCRIPT]";
        }

        if (node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) {
            ret += " [RANK_EXPRESSION";
            if (node.containsExpressionData()) {
                ret += " (EXPRESSSION)";
            } else {
                ret += " (FEATURE LIST)";
            }
            ret += "]";
        }

        if (node.getLanguageType() == LanguageType.RANK_EXPRESSION && (node.getOriginalRankExpressionNode() instanceof BaseNode)) {
            BaseNode originalNode = (BaseNode)node.getOriginalRankExpressionNode();

            if (originalNode != null && originalNode.expressionNode != null) {

                ExpressionNode expressionNode = originalNode.expressionNode;
                String[] classList = expressionNode.getClass().toString().split("[.]");
                String className = classList[classList.length - 1];
                //String msg = expressionNode.toString() + " (" + className + ")";
                ret += " (" + className + ")";

                if (expressionNode instanceof ReferenceNode) {
                    var ref = ((ReferenceNode)expressionNode).reference();

                    ret += " [REF: " + ref.name() + "]";

                    if (ref.isIdentifier()) {
                        ret += " [IDENTIFIER]";
                    } 
                }
            }
        }

        if (node.hasSymbol()) {
            ret += " [SYMBOL " + node.getSymbol().getType().toString() + " " + node.getSymbol().getStatus().toString() + ": " + node.getSymbol().getLongIdentifier() +  "]";
        }

        Range range = node.getRange();
        ret += ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ")";
        ret += " - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")";

        return ret;
    }
}
