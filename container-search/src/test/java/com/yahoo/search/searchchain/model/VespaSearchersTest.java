package com.yahoo.search.searchchain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class VespaSearchersTest {

    @Test
    public void testNativeChain() {
        String requiredSearcher = "com.yahoo.prelude.statistics.StatisticsSearcher";
        assertTrue(VespaSearchers.nativeSearcherModels.stream()
                                                      .anyMatch(searcherModel -> searcherModel.bundleInstantiationSpec.id.getName().equals(requiredSearcher)));
    }

}
