// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains.search;

import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.search.federation.FederationConfig;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.vespa.model.container.search.searchchain.FederationSearcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test of DomFederationSearcherBuilder.
 *
 * @author Tony Vaagenes
 */
public class DomFederationSearcherBuilderTest extends DomBuilderTest {

    @Test
    void ensureCorrectModel() {
        FederationSearcher searcher = new DomFederationSearcherBuilder().doBuild(root.getDeployState(), root, parse(
                "<federation id='theId'>",
                "    <provides>p2</provides>",
                "    <source-set inherits=\"default\" />",
                "    <source id='source1'>",
                "        <federationoptions optional='true' />",
                "    </source>",
                "    <source id='source2' />",
                "</federation>"));

        FederationSearcherModel model = searcher.model;

        assertEquals("theId", model.bundleInstantiationSpec.id.stringValue());
        assertEquals(com.yahoo.search.federation.FederationSearcher.class.getName(),
                model.bundleInstantiationSpec.classId.stringValue());

        assertEquals(2, model.targets.size());
        assertTrue(model.inheritDefaultSources, "source-set option was ignored");

        assertTrue(targetNames(model.targets).containsAll(List.of("source1", "source2")));
    }

    private List<String> targetNames(List<FederationSearcherModel.TargetSpec> targets) {
        List<String> res = new ArrayList<>();
        for (FederationSearcherModel.TargetSpec target : targets) {
            res.add(target.sourceSpec.getName());
        }
        return res;
    }

    @Test
    void require_that_target_selector_can_be_configured() {
        FederationSearcher searcher = new DomFederationSearcherBuilder().doBuild(root.getDeployState(), root, parse(
                "<federation id='federation-id'>",
                "    <target-selector id='my-id' class='my-class' />",
                "</federation>"));

        String targetSelectorId = "my-id@federation-id";

        var targetSelector = searcher.getChildren().get(targetSelectorId);
        assertNotNull(targetSelector, "No target selector child found");

        FederationConfig.Builder builder = new FederationConfig.Builder();
        searcher.getConfig(builder);
        assertEquals(targetSelectorId, new FederationConfig(builder).targetSelector());
    }

}
