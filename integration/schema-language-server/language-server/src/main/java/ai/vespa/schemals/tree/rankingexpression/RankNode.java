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
import ai.vespa.schemals.parser.rankingexpression.ast.lambdaFunction;
import ai.vespa.schemals.parser.rankingexpression.ast.outs;
import ai.vespa.schemals.parser.rankingexpression.ast.scalarOrTensorFunction;
import ai.vespa.schemals.parser.rankingexpression.ast.tensorReduceComposites;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.SpecificFunction;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * RankNode represents a node in the Rank pexression AST.
 * The node can either represent an expression, a feature or a built-in-function
 * 
 * An expression is a rank expression.
 * A feature is a function with parameters, and an optional property. All parameters must be expressions.
 */
public class RankNode implements Iterable<RankNode>  {

    public static enum RankNodeType {
        FEATURE,
        EXPRESSION,
        BUILT_IN_FUNCTION
    };

    public static enum ReturnType {
        INTEGER,
        DOUBLE,
        STRING,
        TENSOR,
        UNKNOWN
    }

    private static Map<Class<?>, ReturnType> BuiltInReturnType = new HashMap<>() {{
        put(tensorReduceComposites.class, ReturnType.TENSOR);
        put(scalarOrTensorFunction.class, ReturnType.DOUBLE);
    }};

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
    private boolean insideLambdaFunction = false;
    private boolean arugmentListExists = false;

    // parameters for features, child nodes for expressions
    private List<RankNode> children;

    // For features, this represents the text after the .
    private Optional<SchemaNode> property;

    // For feature nodes, this specifies the function signature
    private Optional<SpecificFunction> functionSignature = Optional.empty();

    private RankNode(SchemaNode node) {
        this.schemaNode = node;
        this.type = rankNodeTypeMap.get(node.getASTClass());
        this.returnType = ReturnType.UNKNOWN;

        node.setRankNode(this);

        if (this.type == RankNodeType.EXPRESSION) {

            this.children = findChildren(node);

        } else if (this.type == RankNodeType.FEATURE) {

            Optional<List<RankNode>> children = findParameters(node);
            if (children.isPresent()) {
                this.children = children.get();
                arugmentListExists = true;
            } else {
                this.children = new ArrayList<>();
            }

            this.property = findProperty(node);
            if (this.property.isPresent()) {
                this.property.get().setRankNode(this);
            }

        } else if (this.type == RankNodeType.BUILT_IN_FUNCTION) {

            this.children = findBuiltInChildren(node);
            this.returnType = BuiltInReturnType.get(node.getASTClass());
        }

        if (node.isASTInstance(lambdaFunction.class)) {
            setInsideLambdaFunction();
        }
    }

    private static List<RankNode> findChildren(SchemaNode node) {
        List<RankNode> ret = new ArrayList<>();

        for (Node child : node) {
            if (rankNodeTypeMap.containsKey(child.getASTClass())) {
                ret.add(new RankNode(child.getSchemaNode()));
            } else {
                ret.addAll(findChildren(child.getSchemaNode()));
            }
        }

        return ret;
    }

    private static Optional<List<RankNode>> findParameters(SchemaNode node) {
        Node parameterNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).getASTClass() == args.class) {
                parameterNode = node.get(i);
                break;
            }
        }

        if (parameterNode == null) {
            return Optional.empty();
        }

        List<RankNode> ret = new ArrayList<>();

        for (Node child : parameterNode) {
            if (child.getASTClass() == expression.class) {
                ret.add(new RankNode(child.getSchemaNode()));
            }
        }

        return Optional.of(ret);
    }

    private static List<RankNode> findBuiltInChildren(SchemaNode node) {
        List<RankNode> ret = new ArrayList<>();

        for (Node child : node) {
            if (child.getASTClass() == expression.class) {
                ret.add(new RankNode(child.getSchemaNode()));
            }
        }

        return ret;
    }

    private static Optional<SchemaNode> findProperty(SchemaNode node) {
        Node propertyNode = null;

        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).getASTClass() == outs.class) {
                propertyNode = node.get(i);
                break;
            }
        }

        if (propertyNode == null 
            || propertyNode.size() == 0 
            || propertyNode.getRange().getStart().equals(propertyNode.getRange().getEnd())) {
            return Optional.empty();
        }

        return Optional.of(propertyNode.get(0).getSchemaNode());

    }

    private static final Map<Class<?>, RankNodeType> rankNodeTypeMap = new HashMap<>() {{
        put(feature.class, RankNodeType.FEATURE);
        put(expression.class, RankNodeType.EXPRESSION);
        put(scalarOrTensorFunction.class, RankNodeType.BUILT_IN_FUNCTION);
        put(tensorReduceComposites.class, RankNodeType.BUILT_IN_FUNCTION);
        put(lambdaFunction.class, RankNodeType.BUILT_IN_FUNCTION);
    }};

    public static List<RankNode> createTree(SchemaNode node) {
        return findChildren(node);
    }

    public SchemaNode getSchemaNode() {
        return schemaNode;
    }

    public List<RankNode> getChildren() {
        return children;
    }

    public Optional<SchemaNode> getProperty() {
        return property;
    }

    public RankNodeType getType() {
        return type;
    }

    public Optional<SpecificFunction> getFunctionSignature() {
        return functionSignature;
    }

    public void setFunctionSignature(SpecificFunction signature) {
        functionSignature = Optional.of(signature);
    }

    public SchemaNode getSymbolNode() {
        if (type == RankNodeType.EXPRESSION  || schemaNode.size() == 0) {
            return null;
        }

        Node symbolNode = schemaNode.get(0);
        if (!symbolNode.hasSymbol()) {
            return null;
        }

        return symbolNode.getSchemaNode();
    }

    public boolean hasSymbol() {
        return (getSymbolNode() != null);
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

    private void setInsideLambdaFunction() {
        if (insideLambdaFunction) return;

        insideLambdaFunction = true;
        for (RankNode child : children) {
            child.setInsideLambdaFunction();
        }
    }

    public boolean getInsideLambdaFunction() {
        return insideLambdaFunction;
    }

    public boolean getArgumentListExists() {
        return arugmentListExists;
    }

    public String toString() {
        return "[RANK: " + type + "] " + schemaNode.toString() + (hasSymbol() ? " Symbol: " + getSymbol().toString() : "");
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
