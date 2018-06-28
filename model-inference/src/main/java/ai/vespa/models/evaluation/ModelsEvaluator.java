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
