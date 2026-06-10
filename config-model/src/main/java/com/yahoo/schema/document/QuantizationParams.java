// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

public record QuantizationParams(int bits) {

    // TODO mode inferred from presence of distance metric
    //  or should this be chosen on the content nodes instead?

}
