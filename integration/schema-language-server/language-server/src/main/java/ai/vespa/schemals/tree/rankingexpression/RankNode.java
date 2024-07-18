package ai.vespa.schemals.tree.rankingexpression;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.parser.rankingexpression.ast.expression;
import ai.vespa.schemals.parser.rankingexpression.ast.feature;
import ai.vespa.schemals.parser.rankingexpression.ast.outs;
import ai.vespa.schemals.parser.rankingexpression.ast.scalarOrTensorFunction;
import ai.vespa.schemals.tree.SchemaNode;

public class RankNode implements Iterable<RankNode>  {

    public static enum RankNodeType {
        FEATURE,
        EXPRESSION,
        BUILT_IN_TENSOR_FUNCTION
    };

    public static enum ReturnType {
        INTEGER,
        DOUBLE,
        STRING,
        TENSOR,
        UNKNOWN
    }

    public static boolean validReturnType(ReturnType expected, ReturnType recieved) {
        if (expected == recieved) return true;

        if (recieved == ReturnType.UNKNOWN) return true;

        if (recieved == ReturnType.INTEGER) return validReturnType(expected, ReturnType.DOUBLE);

        if (recieved == ReturnType.DOUBLE) return validReturnType(expected, ReturnType.STRING);

        return false;
    }

    private SchemaNode schemaNode;
    private RankNodeType type;
    private ReturnType returnType;

    private List<RankNode> children; // parameters for features, children for expressions

    private Optional<SchemaNode> proptery;

    private RankNode(SchemaNode node) {
        this.schemaNode = node;
        this.type = rankNodeTypeMap.get(node.getASTClass());
        this.returnType = ReturnType.UNKNOWN;

        if (this.type == RankNodeType.EXPRESSION) {

            this.children = findChildren(node);

        } else if (this.type == RankNodeType.FEATURE) {

            this.children = findParameters(node);
            this.proptery = findProperty(node);

        } else if (this.type == RankNodeType.BUILT_IN_TENSOR_FUNCTION) {

            this.children = findParameters(node);
            this.returnType = ReturnType.TENSOR;
        }
    }

    private static List<RankNode> findChildren(SchemaNode node) {
        SchemaNode operationExpressionNode = node.get(0);

        List<RankNode> ret = new ArrayList<>();

        for (SchemaNode child : operationExpressionNode) {
            Optional<RankNode> newNode = createTree(child);
            if (newNode.isPresent()) {
                ret.add(newNode.get());
            }
        }

        return ret;
    }

    private static List<RankNode> findParameters(SchemaNode node) {
        SchemaNode parameterNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).isASTInstance(args.class)) {
                parameterNode = node.get(i);
                break;
            }
        }

        if (parameterNode == null) {
            return new ArrayList<>();
        }

        List<RankNode> ret = new ArrayList<>();

        for (SchemaNode child : parameterNode) {
            if (child.isASTInstance(expression.class)) {
                ret.add(new RankNode(child));
            }
        }

        return ret;
    }

    private static Optional<SchemaNode> findProperty(SchemaNode node) {
        SchemaNode propertyNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).isASTInstance(outs.class)) {
                propertyNode = node.get(i);
                break;
            }
        }

        if (propertyNode == null || propertyNode.size() == 0) {
            return Optional.empty();
        }

        return Optional.of(propertyNode.get(0));

    }

    private static final Map<Class<?>, RankNodeType> rankNodeTypeMap = new HashMap<>() {{
        put(feature.class, RankNodeType.FEATURE);
        put(expression.class, RankNodeType.EXPRESSION);
        put(scalarOrTensorFunction.class, RankNodeType.BUILT_IN_TENSOR_FUNCTION);
    }};

    private static Optional<SchemaNode> findEntrancePoint(SchemaNode node) {
        SchemaNode searchNode = node;

        if (rankNodeTypeMap.containsKey(node.getASTClass())) {
            return Optional.of(searchNode);
        }

        while (searchNode.size() > 0) {
            searchNode = searchNode.get(0);
            if (rankNodeTypeMap.containsKey(searchNode.getASTClass())) {
                return Optional.of(searchNode);
            }
        }

        SchemaNode nextSibling = node.getNextSibling();
        if (nextSibling != null) {
            return findEntrancePoint(nextSibling);
        }

        return Optional.empty();
    }

    public static Optional<RankNode> createTree(SchemaNode node) {
        Optional<SchemaNode> entranceNode = findEntrancePoint(node);
        if (entranceNode.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new RankNode(entranceNode.get()));
    }

    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public List<RankNode> getChildren() {
        return children;
    }

    public Optional<SchemaNode> getProperty() {
        return proptery;
    }

    public RankNodeType getType() {
        return type;
    }

    public SchemaNode getSymbolNode() {
        if (type == RankNodeType.EXPRESSION  || schemaNode.size() == 0) {
            return null;
        }

        SchemaNode symbolNode = schemaNode.get(0);
        if (!symbolNode.hasSymbol()) {
            return null;
        }

        return symbolNode;
    }

    // Ths means that the node has a symbol
    public boolean hasSymbol() {
        return type != RankNodeType.EXPRESSION;
    }

    public Symbol getSymbol() {
        if (!hasSymbol()) return null;

        return getSymbolNode().getSymbol();
    }

    public SymbolType getSymbolType() {
        return getSymbolNode().getSymbol().getType();
    }

    public SymbolStatus getSymbolStatus() {
        return getSymbolNode().getSymbol().getStatus();
    }

    public Range getRange() {
        return schemaNode.getRange();
    }

    public ReturnType getReturnType() {
        return returnType;
    }

    public void setReturnType(ReturnType returnType) {
        this.returnType = returnType;
    }

    public String toString() {
        return "[RANK: " + type + "] " + schemaNode.toString();
    }

    @Override
    public Iterator<RankNode> iterator() {
        return new Iterator<RankNode>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public RankNode next() {
                return children.get(currentIndex++);
			}
        };
    }
}
