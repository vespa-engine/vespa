package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.modelfactory.ModelResult;

import java.util.List;
import java.util.Map;

/**
 * @author jonmv
 */
public interface ActiveTokenFingerprints {

    /** Lists all active token fingerprints for each token-enabled container host in the application, that is currently up. */
    Map<String, List<String>> get(ModelResult application);

}
