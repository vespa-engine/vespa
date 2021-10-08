// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.concurrent.InThreadExecutorService;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.host.HostValidator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Ulf Lilleengen
 */
public class SessionTest {

    public static class MockSessionPreparer extends SessionPreparer {

        public MockSessionPreparer() {
            super(null, null, new InThreadExecutorService(), null, null, null, null, new MockCurator(), null, null, null);
        }

        @Override
        public PrepareResult prepare(HostValidator<ApplicationId> hostValidator, DeployLogger logger, PrepareParams params,
                                     Optional<ApplicationSet> currentActiveApplicationSet,
                                     Instant now, File serverDbSessionDir, ApplicationPackage applicationPackage,
                                     SessionZooKeeperClient sessionZooKeeperClient) {
            return new PrepareResult(AllocatedHosts.withHosts(Set.of()), List.of());
        }
    }

}
