// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.CloudName;
import com.yahoo.vespa.model.VespaModel;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.collections.CollectionUtil.mkString;
import static com.yahoo.vespa.model.application.validation.first.AccessControlOnFirstDeploymentValidator.needsAccessControlValidation;
import static com.yahoo.vespa.model.container.http.AccessControl.hasHandlerThatNeedsProtection;

/**
 * @author gjoranv
 */
public class AwsAccessControlValidator extends Validator {

    // NOTE: must be the same as the name in the declaration of the AWS cloud in hosted.
    static final String AWS_CLOUD_NAME = "aws";

    @Override
    public void validate(VespaModel model, DeployState deployState) {

        if (! needsAccessControlValidation(model, deployState)) return;
        if(! deployState.zone().cloud().equals(CloudName.from(AWS_CLOUD_NAME))) return;

        List<String> offendingClusters = new ArrayList<>();
        for (var cluster : model.getContainerClusters().values()) {
            var http = cluster.getHttp();
            if (http == null
                    || ! http.getAccessControl().isPresent()
                    || ! http.getAccessControl().get().writeEnabled
                    || ! http.getAccessControl().get().readEnabled)

                if (hasHandlerThatNeedsProtection(cluster) || ! cluster.getAllServlets().isEmpty())
                    offendingClusters.add(cluster.getName());
        }
        if (! offendingClusters.isEmpty())
            deployState.validationOverrides()
                    .invalid(ValidationId.accessControl,
                             "Access-control must be enabled for read/write operations to container clusters in AWS production zones: " +
                                     mkString(offendingClusters, "[", ", ", "]"), deployState.now());
    }

}
