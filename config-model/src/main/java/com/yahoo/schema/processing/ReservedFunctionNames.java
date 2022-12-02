// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.schema.RankProfile;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.searchlib.rankingexpression.parser.RankingExpressionParserConstants;
import com.yahoo.vespa.model.container.search.QueryProfiles;

import java.util.Set;
import java.util.logging.Level;

/**
 * Issues a warning if some function has a reserved name. This is not necessarily
 * an error, as a rank profile function can shadow a built-in function.
 *
 * @author lesters
 */
public class ReservedFunctionNames extends Processor {

    private static Set<String> reservedNames = getReservedNames();

    public ReservedFunctionNames(Schema schema, DeployLogger deployLogger, RankProfileRegistry rankProfileRegistry, QueryProfiles queryProfiles) {
        super(schema, deployLogger, rankProfileRegistry, queryProfiles);
    }

    @Override
    public void process(boolean validate, boolean documentsOnly) {
        if ( ! validate) return;
        if (documentsOnly) return;

        for (RankProfile rp : rankProfileRegistry.all()) {
            for (String functionName : rp.getFunctions().keySet()) {
                if (reservedNames.contains(functionName)) {
                    deployLogger.logApplicationPackage(Level.WARNING, "Function '" + functionName + "' " +
                                                                      "in rank profile '" + rp.name() + "' " +
                                                                      "has a reserved name. This might mean that the function shadows " +
                                                                      "the built-in function with the same name."
                    );
                }
            }
        }
    }

    private static ImmutableSet<String> getReservedNames() {
        ImmutableSet.Builder<String> names = ImmutableSet.builder();
        for (String token : RankingExpressionParserConstants.tokenImage) {
            String tokenWithoutQuotes = token.substring(1, token.length()-1);
            names.add(tokenWithoutQuotes);
        }
        return names.build();
    }

}
