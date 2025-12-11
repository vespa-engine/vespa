// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.xgboost;

/**
 * Base class for XGBoost parsers containing shared tree-to-expression conversion logic.
 *
 * @author arnej
 */
abstract class AbstractXGBoostParser {

    /**
     * Converts an XGBoostTree node to a Vespa ranking expression string.
     * This method handles both leaf nodes and split nodes recursively.
     *
     * @param node XGBoost tree node to convert.
     * @return Vespa ranking expression for input node.
     */
    protected String treeToRankExp(XGBoostTree node) {
        if (node.isLeaf()) {
            return Double.toString(node.getLeaf());
        } else {
            assert node.getChildren().size() == 2;
            String trueExp;
            String falseExp;
            if (node.getYes() == node.getChildren().get(0).getNodeid()) {
                trueExp = treeToRankExp(node.getChildren().get(0));
                falseExp = treeToRankExp(node.getChildren().get(1));
            } else {
                trueExp = treeToRankExp(node.getChildren().get(1));
                falseExp = treeToRankExp(node.getChildren().get(0));
            }
            // xgboost uses float only internally, so round to closest float
            float xgbSplitPoint = (float)node.getSplit_condition();
            // but Vespa expects rank profile literals in double precision:
            double vespaSplitPoint = xgbSplitPoint;
            String condition;
            if (node.getMissing() == node.getYes()) {
                // Note: this is for handling missing features, as the backend handles comparison with NaN as false.
                condition = "!(" + node.getSplit() + " >= " + vespaSplitPoint + ")";
            } else {
                condition = node.getSplit() + " < " + vespaSplitPoint;
            }
            return "if (" + condition + ", " + trueExp + ", " + falseExp + ")";
        }
    }

}
