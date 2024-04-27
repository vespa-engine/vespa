// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.significance;

import com.yahoo.api.annotations.Beta;
import com.yahoo.language.Language;

import java.util.Optional;

/**
 * @author MariusArhaug
 */
@Beta
public interface SignificanceModelRegistry {
    Optional<SignificanceModel> getModel(Language language);
}
