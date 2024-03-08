// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.Schema;
import com.yahoo.vespa.documentmodel.SummaryField;
import com.yahoo.vespa.documentmodel.SummaryTransform;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generated juniperrc-config for controlling juniper.
 *
 * @author Simon Thoresen Hult
 */
public class Juniperrc extends Derived {

    private static final int Mb = 1024 * 1024;

    /** List of all fields that should be bolded. */
    private final Set<String> boldingFields = new java.util.LinkedHashSet<>();

    /**
     * Constructs a new juniper rc instance for a given search object.
     * This will derive the configuration automatically,
     * so there is no need to call {@link #derive(Schema)}.
     *
     * @param schema the search model to use for deriving
     */
    public Juniperrc(Schema schema) {
        derive(schema);
    }

    @Override
    protected void derive(Schema schema) {
        super.derive(schema);
        for (SummaryField summaryField : schema.getUniqueNamedSummaryFields().values()) {
            if (summaryField.getTransform() == SummaryTransform.BOLDED) {
                boldingFields.add(summaryField.getName());
            }
        }
    }

    public void export(String toDirectory) throws IOException {
        var builder = new JuniperrcConfig.Builder();
        getConfig(builder);
        export(toDirectory, builder.build());
    }

    @Override protected String getDerivedName() { return "juniperrc"; }

    private static JuniperrcConfig.Override.Builder createOverride(String name) {
        return new JuniperrcConfig.Override.Builder()
                .fieldname(name)
                .length(64*Mb)
                .max_matches(1)
                .min_length(8192)
                .surround_max(64*Mb);
    }

    public void getConfig(JuniperrcConfig.Builder builder) {
        // Replace
        if (!boldingFields.isEmpty()) {
            builder.prefix(true);
            // Needs an modifiable list for config overrides ....
            builder.override(boldingFields.stream().map(Juniperrc::createOverride).collect(Collectors.toCollection(() -> new ArrayList<>())));
        }
    }

}
