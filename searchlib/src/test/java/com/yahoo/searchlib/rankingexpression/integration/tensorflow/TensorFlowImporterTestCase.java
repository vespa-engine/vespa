package com.yahoo.searchlib.rankingexpression.integration.tensorflow;

import org.junit.Test;

/**
 * @author bratseth
 */
public class TensorFlowImporterTestCase {
    
    @Test
    public void testModel1() {
        new TensorFlowImporter().importModel("src/test/files/integration/tensorflow/model1/");
    }
    
}
