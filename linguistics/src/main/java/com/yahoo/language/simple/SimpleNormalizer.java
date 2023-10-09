// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.simple;

import com.yahoo.language.process.Normalizer;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleNormalizer implements Normalizer {

    @Override
    public String normalize(String input) {
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFKC);
    }

}
