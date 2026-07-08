// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

/**
 * A production rule which <i>adds</i> the production to the matched query
 *
 * @author bratseth
 */
public class AddingProductionRule extends ProductionRule {

    protected String getSymbol() { return "+>"; }

    @Override
    public void setProduction(ProductionList productionList) {
        super.setProduction(productionList);
        productionList.setReplacing(false);
    }

}
