package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.model.VespaModel;

/**
 * Validates that a Vespa Cloud application has at least one container cluster.
 *
 * @author jonmv
 */
public class ContainerInCloudValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        if (deployState.isHosted() && model.getContainerClusters().isEmpty())
            throw new IllegalArgumentException("Vespa Cloud applications must have at least one container cluster");
    }

}
