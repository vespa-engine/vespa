// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;

/**
 * A rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final SdRankProfileDefinition definition;

    public RankProfile(SdRankProfileDefinition definition) {
        this.definition = definition;
    }

}
