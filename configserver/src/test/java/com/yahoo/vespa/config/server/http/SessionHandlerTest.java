// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.common.io.Files;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.log.LogLevel;
import com.yahoo.path.Path;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationSet;
import com.yahoo.vespa.config.server.configchange.ConfigChangeActions;
import com.yahoo.vespa.config.server.host.HostRegistry;
import com.yahoo.vespa.config.server.session.DummyTransaction;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.MockSessionZKClient;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.session.SessionContext;
import com.yahoo.vespa.config.server.session.SessionFactory;
import com.yahoo.vespa.flags.InMemoryFlagSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for session handler tests
 *
 * @author hmusum
 */
public class SessionHandlerTest {

    protected String pathPrefix = "/application/v2/session/";
    public static final String hostname = "foo";
    public static final int port = 1337;


    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath, InputStream data, Map<String, String> properties) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method, data, properties);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath, InputStream data) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method, data);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id) {
        return createTestRequest(path, method, cmd, id, "");
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path, method);
    }

    public static HttpRequest createTestRequest(String path) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path, com.yahoo.jdisc.http.HttpRequest.Method.PUT);
    }

    public static String getRenderedString(HttpResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    public static class MockSession extends LocalSession {

        public boolean doVerboseLogging = false;
        public Session.Status status;
        private ConfigChangeActions actions = new ConfigChangeActions();
        private long createTime = System.currentTimeMillis() / 1000;
        private ApplicationId applicationId;
        private Optional<String> dockerImageRepository;

        public MockSession(long id, ApplicationPackage app) {
            this(id, app, new InMemoryFlagSource());
        }

        private MockSession(long id, ApplicationPackage app, InMemoryFlagSource flagSource) {
            super(TenantName.defaultName(), id, null, new SessionContext(app, new MockSessionZKClient(app), null, null, new HostRegistry<>(), flagSource));
        }

        public MockSession(long sessionId, ApplicationPackage applicationPackage, long createTime) {
            this(sessionId, applicationPackage);
            this.createTime = createTime;
        }

        public MockSession(long sessionId, ApplicationPackage applicationPackage, ConfigChangeActions actions) {
            this(sessionId, applicationPackage);
            this.actions = actions;
        }

        public MockSession(long sessionId, ApplicationPackage app, ApplicationId applicationId) {
            this(sessionId, app);
            this.applicationId = applicationId;
        }

        @Override
        public ConfigChangeActions prepare(DeployLogger logger, PrepareParams params, Optional<ApplicationSet> application, Path tenantPath, Instant now) {
            status = Session.Status.PREPARE;
            this.dockerImageRepository = params.dockerImageRepository();
            if (doVerboseLogging) {
                logger.log(LogLevel.DEBUG, "debuglog");
            }
            return actions;
        }

        public void setStatus(Session.Status status) {
            this.status = status;
        }

        @Override
        public Session.Status getStatus() {
            return this.status;
        }

        @Override
        public Transaction createDeactivateTransaction() {
            return new DummyTransaction().add((DummyTransaction.RunnableOperation) () -> status = Status.DEACTIVATE);
        }

        @Override
        public Transaction createActivateTransaction() {
            return new DummyTransaction().add((DummyTransaction.RunnableOperation) () -> status = Status.ACTIVATE);
        }

        @Override
        public ApplicationFile getApplicationFile(Path relativePath, Mode mode) {
            return this.applicationPackage.getFile(relativePath);
        }

        @Override
        public ApplicationId getApplicationId() {
            return applicationId;
        }

        @Override
        public long getCreateTime() {
            return createTime;
        }

        @Override
        public void delete(NestedTransaction transaction) {  }

        @Override
        public Optional<String> getDockerImageRepository() {
            return dockerImageRepository;
        }
    }

    public enum Cmd {
        PREPARED("prepared"),
        ACTIVE("active"),
        CONTENT("content");
        private final String name;

        Cmd(String s) {
            this.name = s;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class MockSessionFactory implements SessionFactory {
        public boolean createCalled = false;
        public boolean createFromCalled = false;
        public boolean doThrow = false;
        public File applicationPackage;

        @Override
        public LocalSession createSession(File applicationDirectory, ApplicationId applicationId, TimeoutBudget timeoutBudget) {
            createCalled = true;
            if (doThrow) {
                throw new RuntimeException("foo");
            }
            final File tempDir = Files.createTempDir();
            try {
                IOUtils.copyDirectory(applicationDirectory, tempDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.applicationPackage = tempDir;
            return new SessionHandlerTest.MockSession(0, FilesApplicationPackage.fromFile(applicationPackage));
        }

        @Override
        public LocalSession createSessionFromExisting(LocalSession existingSession, DeployLogger logger,
                                                      boolean internalRedeploy, TimeoutBudget timeoutBudget) {
            if (doThrow) {
                throw new RuntimeException("foo");
            }
            createFromCalled = true;
            return new SessionHandlerTest.MockSession(existingSession.getSessionId() + 1, FilesApplicationPackage.fromFile(applicationPackage));
        }
    }

    public static class MockProvisioner implements Provisioner {

        public boolean activated = false;
        public boolean removed = false;
        public boolean restarted = false;
        public ApplicationId lastApplicationId;
        public Collection<HostSpec> lastHosts;

        @Override
        @Deprecated // TODO: Remove after April 2020
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, int groups, ProvisionLogger logger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HostSpec> prepare(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity, ProvisionLogger logger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
            activated = true;
            lastApplicationId = application;
            lastHosts = hosts;
        }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) {
            removed = true;
            lastApplicationId = application;
        }

        @Override
        public void restart(ApplicationId application, HostFilter filter) {
            restarted = true;
            lastApplicationId = application;
        }

    }

    public static class FailingMockProvisioner extends MockProvisioner {

        @Override
        public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
            throw new IllegalArgumentException("Cannot activate application");
        }

        @Override
        public void remove(NestedTransaction transaction, ApplicationId application) {
            throw new IllegalArgumentException("Cannot remove application");
        }

    }
}
