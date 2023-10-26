// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Simon Thoresen Hult
 */
public class GbdtModel {

    private final List<TreeNode> trees;

    public GbdtModel(List<TreeNode> trees) {
        this.trees = asForest(trees);
    }

    public List<TreeNode> trees() {
        return trees;
    }

    public String toRankingExpression() {
        if ( ! hasSampleInformation())
            System.err.println("The model nodes does not have the 'nSamples' attribute. " +
                               "For optimal runtime performance use an 'ext' model which has this information.");
        StringBuilder ret = new StringBuilder();
        for (TreeNode tree : trees) {
            if (ret.length() > 0) {
                ret.append(" +\n");
            }
            ret.append(tree.toRankingExpression());
        }
        ret.append("\n");
        return ret.toString();
    }

    /**
     * Return whether this model has sample information.
     * Don't bother to check every node as files either has this for all nodes or for none.
     */
    private boolean hasSampleInformation() {
        if (trees.size() == 0) return true; // no matter
        return trees.get(0).samples() !=null;
    }

    public static GbdtModel fromXml(String xml) throws ParserConfigurationException, IOException, SAXException {
        return fromDom(XmlHelper.parseXml(xml));
    }

    public static GbdtModel fromXmlFile(String fileName) throws ParserConfigurationException, IOException, SAXException {
        return fromDom(XmlHelper.parseXmlFile(fileName));
    }

    public static GbdtModel fromDom(Node doc) {
        Element dtree = XmlHelper.getSingleElement(doc, "DecisionTree");
        Element forest = XmlHelper.getSingleElement(dtree, "Forest");
        List<Element> trees = XmlHelper.getChildElements(forest, "Tree");
        if (trees.isEmpty()) {
            throw new IllegalArgumentException("Forest has no trees.");
        }
        List<TreeNode> model = new ArrayList<>();
        for (Node tree : trees) {
            if (XmlHelper.getChildElements(tree, null).isEmpty()) continue; // ignore
            model.add(TreeNode.fromDom(XmlHelper.getSingleElement(tree, null)));
        }
        return new GbdtModel(model);
    }

    private static List<TreeNode> asForest(List<TreeNode> in) {
        List<TreeNode> out = new ArrayList<>(in.size());
        for (TreeNode node : in) {
            if (node instanceof FeatureNode) {
                out.add(node);
            } else if (node instanceof ResponseNode) { // TODO): We should stop this sillyness ...
                out.add(new NumericFeatureNode("value(0)", new DoubleValue(1), node.samples(), node,
                                                           new ResponseNode(0, Optional.of(0))));
            } else {
                throw new UnsupportedOperationException(node.getClass().getName());
            }
        }
        return Collections.unmodifiableList(out);
    }
}
