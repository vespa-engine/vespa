// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.google.common.collect.ImmutableList;
import com.yahoo.collections.Pair;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;
import static com.yahoo.config.model.test.MockApplicationPackage.MUSIC_SEARCHDEFINITION;
import static com.yahoo.config.model.test.MockApplicationPackage.BOOK_SEARCHDEFINITION;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author bratseth
 */
public class ValidationTester {

    private final int nodeCount;

    /** Creates a validation tester with 1 node available */
    public ValidationTester() {
        this(1);
    }

    /** Creates a validation tester with a number of nodes available */
    public ValidationTester(int nodeCount) {
        this.nodeCount = nodeCount;
    }

    /**
     * Deploys an application
     *
     * @param previousModel the previous model, or null if no previous
     * @param services the services file content
     * @param validationOverrides the validation overrides file content, or null if none
     * @return the new model and any change actions
     */
    public Pair<VespaModel, List<ConfigChangeAction>> deploy(VespaModel previousModel, String services, String validationOverrides) {
        Instant now = LocalDate.parse("2000-01-01", DateTimeFormatter.ISO_DATE).atStartOfDay().atZone(ZoneOffset.UTC).toInstant();
        ApplicationPackage newApp = new MockApplicationPackage.Builder()
                .withServices(services)
                .withSearchDefinitions(ImmutableList.of(MUSIC_SEARCHDEFINITION, BOOK_SEARCHDEFINITION))
                .withValidationOverrides(validationOverrides)
                .build();
        VespaModelCreatorWithMockPkg newModelCreator = new VespaModelCreatorWithMockPkg(newApp);
        DeployState.Builder deployStateBuilder = new DeployState.Builder()
                                                             .applicationPackage(newApp)
                                                             .properties(new DeployProperties.Builder().hostedVespa(true).build())
                                                             .modelHostProvisioner(new InMemoryProvisioner(nodeCount))
                                                             .now(now);
        if (previousModel != null)
            deployStateBuilder.previousModel(previousModel);
        VespaModel newModel = newModelCreator.create(deployStateBuilder);
        return new Pair<>(newModel, newModelCreator.configChangeActions);
    }



}
