// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

/**
 * A production rule which <i>adds</i> the production to the matched query
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon S Bratseth</a>
 */
public class AddingProductionRule extends ProductionRule {

    protected String getSymbol() { return "+>"; }

    public void setProduction(ProductionList productionList) {
        super.setProduction(productionList);
        productionList.setReplacing(false);
    }

}
