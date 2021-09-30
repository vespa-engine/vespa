// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.container.QrConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class ContainerRestartValidatorTest {

    @Test
    public void validator_returns_action_for_containers_with_restart_on_deploy_enabled() {
        VespaModel current = createModel(true);
        VespaModel next = createModel(true);
        List<ConfigChangeAction> result = validateModel(current, next);
        assertEquals(2, result.size());
    }

    @Test
    public void validator_returns_empty_list_for_containers_with_restart_on_deploy_disabled() {
        VespaModel current = createModel(false);
        VespaModel next = createModel(false);
        List<ConfigChangeAction> result = validateModel(current, next);
        assertTrue(result.isEmpty());
    }

    @Test
    public void validator_returns_empty_list_for_containers_with_restart_on_deploy_disabled_where_previously_enabled() {
        VespaModel current = createModel(true);
        VespaModel next = createModel(false);
        List<ConfigChangeAction> result = validateModel(current, next);
        assertTrue(result.isEmpty());
    }

    @Test
    public void restart_on_deploy_is_propagated_to_cluster() {
        VespaModel model1 = createModel(false);
        assertFalse(model1.getContainerClusters().get("cluster1").getDeferChangesUntilRestart());
        assertFalse(model1.getContainerClusters().get("cluster2").getDeferChangesUntilRestart());
        assertFalse(model1.getContainerClusters().get("cluster3").getDeferChangesUntilRestart());

        VespaModel model2 = createModel(true);
        assertTrue(model2.getContainerClusters().get("cluster1").getDeferChangesUntilRestart());
        assertTrue(model2.getContainerClusters().get("cluster2").getDeferChangesUntilRestart());
        assertFalse(model2.getContainerClusters().get("cluster3").getDeferChangesUntilRestart());
    }

    private static List<ConfigChangeAction> validateModel(VespaModel current, VespaModel next) {
        return new ContainerRestartValidator()
                .validate(current, next, new ValidationOverrides(Collections.emptyList()), Instant.now());
    }

    private static VespaModel createModel(boolean restartOnDeploy) {
        return new VespaModelCreatorWithMockPkg(
                null,
                "<?xml version='1.0' encoding='utf-8' ?>\n" +
                "<services version='1.0'>\n" +
                "    <container id='cluster1' version='1.0'>\n" +
                "       <http>\n" +
                "           <server id='server1' port='" + Defaults.getDefaults().vespaWebServicePort() + "'/>\n" +
                "       </http>\n" +
                "       <config name='container.qr'>\n" +
                "           <restartOnDeploy>" + restartOnDeploy + "</restartOnDeploy>\n" +
                "       </config>\n" +
                "   </container>\n" +
                "   <container id='cluster2' version='1.0'>\n" +
                "       <http>\n" +
                "           <server id='server2' port='4090'/>\n" +
                "       </http>\n" +
                "       <config name='container.qr'>\n" +
                "           <restartOnDeploy>" + restartOnDeploy + "</restartOnDeploy>\n" +
                "       </config>\n" +
                "   </container>\n" +
                "   <container id='cluster3' version='1.0'>\n" +
                "       <http>\n" +
                "           <server id='server3' port='4100'/>\n" +
                "       </http>\n" +
                "   </container>\n" +
                "</services>"
        ).create();
    }

}
