// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.deploy;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestWithTenant;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.SilentDeployLogger;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class HostedDeployTest extends TestWithTenant {

    private static final Path appPath = Path.createRoot().append("testapp");
    private File testApp = new File("src/test/apps/hosted/");
    private Path tenantPath = appPath;

    @Test
    public void testRedeploy() throws InterruptedException, IOException {
        ApplicationId id = deployApp();

        ApplicationRepository applicationRepository = new ApplicationRepository(tenants, HostProvisionerProvider.withProvisioner(createHostProvisioner()),
                                                                                new ConfigserverConfig(new ConfigserverConfig.Builder()), curator);

        Optional<com.yahoo.config.provision.Deployment> deployment = applicationRepository.deployFromLocalActive(id, Duration.ofSeconds(60));
        assertTrue(deployment.isPresent());
        deployment.get().prepare();
        deployment.get().activate();
    }

    /**
     * Do the initial "deploy" with the existing API-less code as the deploy API doesn't support first deploys yet.
     */
    private ApplicationId deployApp() throws InterruptedException, IOException {
        LocalSession session = tenant.getSessionFactory().createSession(testApp, "default", new SilentDeployLogger(), new TimeoutBudget(Clock.systemUTC(), Duration.ofSeconds(60)));
        ApplicationId id = ApplicationId.from(tenant.getName(), ApplicationName.from("myapp"), InstanceName.defaultName());
        session.prepare(new SilentDeployLogger(), new PrepareParams(new ConfigserverConfig(new ConfigserverConfig.Builder())).applicationId(id), Optional.empty(), tenantPath);
        session.createActivateTransaction().commit();
        tenant.getLocalSessionRepo().addSession(session);
        return id;
    }

    private Provisioner createHostProvisioner() {
        return new ProvisionerAdapter(new InMemoryProvisioner(true, "host0", "host1", "host2"));
    }

    private static class ProvisionerAdapter implements Provisioner {

        private final HostProvisioner hostProvisioner;

        public ProvisionerAdapter(HostProvisioner hostProvisioner) {
            this.hostProvisioner = hostProvisioner;
        }

        @Override
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) {
            return hostProvisioner.prepare(cluster, capacity, groups, logger);
        }

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
            // noop
        }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) {
            // noop
        }

        @Override
        public void restart(ApplicationId application, HostFilter filter) {
            // noop
        }

    }

}
