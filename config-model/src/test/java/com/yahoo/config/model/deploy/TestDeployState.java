package com.yahoo.config.model.deploy;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;

/**
 * Convenience class for unit testing where DeployState is needed
 *
 * @author hmusum
 */
public class TestDeployState {

    public static DeployState.Builder createBuilder() {
        return new DeployState.Builder().properties(new TestProperties());
    }

    public static DeployState create() {
        return createBuilder().build();
    }

    public static DeployState create(DeployLogger testLogger) {
        return createBuilder()
                .deployLogger(testLogger)
                .build();
    }

    public static DeployState create(ApplicationPackage applicationPackage) {
        return createBuilder()
                .applicationPackage(applicationPackage)
                .build();
    }

    public static DeployState create(DeployLogger testLogger, ApplicationPackage applicationPackage) {
        return createBuilder()
                .deployLogger(testLogger)
                .applicationPackage(applicationPackage)
                .build();
    }

}
