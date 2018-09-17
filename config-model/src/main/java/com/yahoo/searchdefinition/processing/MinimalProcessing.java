package com.yahoo.searchdefinition.processing;

import java.util.Collection;

public class MinimalProcessing extends Processing {
    @Override
    protected Collection<ProcessorFactory> createProcessorFactories() {
        return minimalSetOfProcessors();
    }
}
