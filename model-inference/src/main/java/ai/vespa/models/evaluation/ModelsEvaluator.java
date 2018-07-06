// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.models.evaluation;

import com.yahoo.vespa.config.search.RankProfilesConfig;

/**
 * Evaluates machine-learned models added to Vespa applications and available as config form.
 *
 * @author bratseth
 */
public class ModelsEvaluator {

    public ModelsEvaluator(RankProfilesConfig config) {
        new RankProfilesConfigImporter().importFrom(config);
    }



}
