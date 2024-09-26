package ai.vespa.schemals.tree;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.tree.YQL.YQLUtils;
import ai.vespa.schemals.tree.grouping.GroupingUtils;

public class YQLNode extends ai.vespa.schemals.tree.Node<YQLNode> {
    
    private Node originalYQLNode;
    private ai.vespa.schemals.parser.grouping.Node originalGroupingNode;

    public YQLNode(Node node) {
        super(LanguageType.YQLPlus, YQLUtils.getNodeRange(node), node.isDirty());
        originalYQLNode = node;

        for (Node child : node.children()) {
            addChild(new YQLNode(child));
        }
    }

    public YQLNode(ai.vespa.schemals.parser.grouping.Node node) {
        super(LanguageType.GROUPING, GroupingUtils.getNodeRange(node), node.isDirty());
        originalGroupingNode = node;

        for (ai.vespa.schemals.parser.grouping.Node child : node.children()) {
            addChild(new YQLNode(child));
        }
    }

    public YQLNode(Range range) {
        super(LanguageType.CUSTOM, range, false);
    }

    public Class<?> getASTClass() {
        if (language == LanguageType.CUSTOM) return YQLNode.class;
        if (language == LanguageType.YQLPlus) return originalYQLNode.getClass();
        if (language == LanguageType.GROUPING) return originalGroupingNode.getClass();
        
        throw new RuntimeException("The YQLNode has an invalid languageType");
    }
}
