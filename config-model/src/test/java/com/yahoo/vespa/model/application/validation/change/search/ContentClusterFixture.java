// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.utils.ContentClusterBuilder;
import com.yahoo.vespa.model.content.utils.ContentClusterUtils;
import com.yahoo.vespa.model.content.utils.SearchDefinitionBuilder;
import com.yahoo.vespa.model.search.DocumentDatabase;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Test fixture to setup current and next content clusters used for change validation.
 *
 * @author geirst
 */
public abstract class ContentClusterFixture {

    protected ContentCluster currentCluster;
    protected ContentCluster nextCluster;

    public ContentClusterFixture(String currentSd, String nextSd) throws Exception {
        currentCluster = createCluster(currentSd);
        nextCluster = createCluster(nextSd);
    }

    private static ContentCluster createCluster(String sdContent) throws Exception {
        return new ContentClusterBuilder().build(
                ContentClusterUtils.createMockRoot(
                        Arrays.asList(new SearchDefinitionBuilder().content(sdContent).build())));
    }

    protected DocumentDatabase currentDb() {
        return currentCluster.getSearch().getIndexed().getDocumentDbs().get(0);
    }

    protected NewDocumentType currentDocType() {
        return currentCluster.getDocumentDefinitions().get("test");
    }

    protected DocumentDatabase nextDb() {
        return nextCluster.getSearch().getIndexed().getDocumentDbs().get(0);
    }

    protected NewDocumentType nextDocType() {
        return nextCluster.getDocumentDefinitions().get("test");
    }

    public void assertValidation() {
        List<VespaConfigChangeAction> act = validate();
        assertThat(act.size(), is(0));
    }

    public void assertValidation(VespaConfigChangeAction exp) {
        assertValidation(Arrays.asList(exp));
    }

    public void assertValidation(List<VespaConfigChangeAction> exp) {
        List<VespaConfigChangeAction> act = validate();
        assertThat(act, equalTo(exp));
    }

    public abstract List<VespaConfigChangeAction> validate();

}

