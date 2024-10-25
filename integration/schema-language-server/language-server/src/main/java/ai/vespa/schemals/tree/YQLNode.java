package ai.vespa.schemals.tree;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLNode {
    
    private Node originalYQLNode;

    private Range range;

    private List<YQLNode> children;

    public YQLNode(Node node) {
        originalYQLNode = node;
        range = YQLUtils.getNodeRange(node);

        children = new ArrayList<>();

        for (Node child : node.children()) {
            children.add(new YQLNode(child));
        }
    }

    public Range getRange() { return range; }
    

}
