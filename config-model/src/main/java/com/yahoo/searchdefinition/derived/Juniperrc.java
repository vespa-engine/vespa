// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.Search;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;

import java.util.Set;

/**
 * Generated juniperrc-config for controlling juniper.
 *
 * @author Simon Thoresen Hult
 */
public class Juniperrc extends Derived implements JuniperrcConfig.Producer {

    // List of all fields that should be bolded.
    private Set<String> boldingFields = new java.util.LinkedHashSet<>();

    /**
     * Constructs a new juniper rc instance for a given search object. This will derive the configuration automatically,
     * so there is no need to call {@link #derive(com.yahoo.searchdefinition.Search)}.
     *
     * @param search The search model to use for deriving.
     */
    public Juniperrc(Search search) {
        derive(search);
    }

    // Inherit doc from Derived.
    @Override
    protected void derive(Search search) {
        super.derive(search);
        for (SummaryField summaryField : search.getUniqueNamedSummaryFields().values()) {
            if (summaryField.getTransform() == SummaryTransform.BOLDED) {
                boldingFields.add(summaryField.getName());
            }
        }
    }

    // Inherit doc from Derived.
    @Override
    protected String getDerivedName() {
        return "juniperrc";
    }

    @Override
    public void getConfig(JuniperrcConfig.Builder builder) {
        if (boldingFields.size() != 0) {
            builder.prefix(true);
            for (String name : boldingFields) {
                builder.override(new JuniperrcConfig.Override.Builder()
                    .fieldname(name)
                    .length(65536)
                    .max_matches(1)
                    .min_length(8192)
                    .surround_max(65536));
            }
        }
    }
}
