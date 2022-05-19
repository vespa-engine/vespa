// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import java.util.List;

/**
 * A rank profile which ignores all calls made to it which may fail in a document only setting.
 * This is used by the search definition parser when it is requested to parse documents only,
 * to avoid having to check for this in every method which adds to the rank profile.
 * (And why do we ever want to parse documents only? Because it is used when generating Java classes
 * from documents, where the full application package may not be available.)
 *
 * @author bratseth
 */
public class DocumentsOnlyRankProfile extends RankProfile {

    public DocumentsOnlyRankProfile(String name, Schema schema, RankProfileRegistry rankProfileRegistry) {
        super(name, schema, rankProfileRegistry);
    }

    @Override
    public void setFirstPhaseRanking(String expression) {
        // Ignore
    }

    @Override
    public void setSecondPhaseRanking(String expression) {
        // Ignore
    }

    @Override
    public void addFunction(String name, List<String> arguments, String expression, boolean inline) {
        // Ignore
    }

}
