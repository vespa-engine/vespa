package ai.vespa.schemals.tree;

import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLNode extends ai.vespa.schemals.tree.Node<YQLNode> {
    
    private Node originalYQLNode;

    public YQLNode(Node node) {
        super(LanguageType.YQLPlus, YQLUtils.getNodeRange(node));
        originalYQLNode = node;

        for (Node child : node.children()) {
            addChild(new YQLNode(child));
        }
    }

    public Class<?> getASTClass() {
        return originalYQLNode.getClass();
    }
}
