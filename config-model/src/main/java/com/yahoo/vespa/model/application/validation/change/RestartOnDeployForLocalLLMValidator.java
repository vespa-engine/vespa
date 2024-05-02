// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;

import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

/**
 * If using local LLMs, this validator will make sure that restartOnDeploy is set for
 * configs for this cluster.
 *
 * @author lesters
 */
public class RestartOnDeployForLocalLLMValidator implements ChangeValidator {

    private static final Logger log = Logger.getLogger(RestartOnDeployForLocalLLMValidator.class.getName());

    @Override
    public void validate(ChangeContext context) {

        for (var cluster : context.model().getContainerClusters().values()) {

            // For now, if a local LLM is used, force a restart of the services
            // Later, be more sophisticated and only restart if redeploy does not fit in (GPU) memory
            cluster.getAllComponents().forEach(component -> {
                if (component.getClassId().getName().equals("ai.vespa.llm.clients.LocalLLM")) {
                    String message = "Restarting services in %s because of local LLM use".formatted(cluster);
                    log.log(INFO, message);
                    context.require(new VespaRestartAction(cluster.id(), message));
                }
            });

        }
    }

}
