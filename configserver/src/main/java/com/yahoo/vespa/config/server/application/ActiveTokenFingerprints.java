// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.modelfactory.ModelResult;

import java.util.List;
import java.util.Map;

/**
 * @author jonmv
 */
public interface ActiveTokenFingerprints {

    /** Lists all active tokens and their fingerprints for each token-enabled container host in the application, that is currently up. */
    Map<String, List<Token>> get(ModelResult application);

    record Token(String id, List<String> fingerprints) { }

}
