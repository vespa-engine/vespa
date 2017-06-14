// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.mlr.gbdt;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Run an expression analyser without having to muck with classpath.
 *
 * @author bratseth
 */
public class ExpressionAnalysisRunner {

    @Test @Ignore
    public void runAnalysis() {
        ExpressionAnalysis.main(new String[] { "/Users/bratseth/Downloads/getty_mlr_001.expression"});
    }

}
