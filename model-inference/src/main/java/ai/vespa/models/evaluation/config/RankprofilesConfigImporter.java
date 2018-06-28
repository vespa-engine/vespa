package ai.vespa.models.evaluation.config;

import com.yahoo.vespa.config.search.RankProfilesConfig;

/**
 * Converts RankprofilesConfig instances to RankingExpressions for evaluation
 */
public class RankprofilesConfigImporter {

    public void importFrom(RankProfilesConfig config) {
        for (RankProfilesConfig.Rankprofile profile : config.rankprofile()) {

        }
    }

}
