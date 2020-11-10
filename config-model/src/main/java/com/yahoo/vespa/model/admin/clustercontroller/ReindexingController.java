// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.config.model.api.Reindexing;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.Collection;

/**
 * @author bjorncs
 */
class ReindexingController extends SimpleComponent implements ReindexingConfig.Producer {

    static final String REINDEXING_CONTROLLER_BUNDLE = "clustercontroller-reindexer";

    private final Reindexing reindexing;
    private final String contentClusterName;
    private final Collection<NewDocumentType> documentTypes;

    ReindexingController(ReindexingContext context) {
        super(new ComponentModel(
                BundleInstantiationSpecification.getFromStrings(
                        "reindexing-maintainer",
                        "ai.vespa.reindexing.ReindexingMaintainer",
                        REINDEXING_CONTROLLER_BUNDLE)));
        this.reindexing = context.reindexing().orElse(null);
        this.contentClusterName = context.contentClusterName();
        this.documentTypes = context.documentTypes();
    }

    @Override
    public void getConfig(ReindexingConfig.Builder builder) {
        builder.clusterName(contentClusterName);
        if (reindexing == null || !reindexing.enabled()) {
            builder.enabled(false);
            return;
        }
        builder.enabled(true);
        for (NewDocumentType type : documentTypes) {
            String typeName = type.getFullName().getName();
            reindexing.status(contentClusterName, typeName).ifPresent(status ->
                    builder.status(
                            typeName,
                            new ReindexingConfig.Status.Builder()
                                    .readyAtMillis(status.ready().toEpochMilli())));
        }
    }
}
