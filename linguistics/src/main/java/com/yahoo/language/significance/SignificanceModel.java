// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.yahoo.api.annotations.Beta;

/**
 * @author MariusArhaug
 */
@Beta
public interface SignificanceModel {
    DocumentFrequency documentFrequency(String word);
}
