// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4.model;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken.DataplaneTokenVersions;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.yolean.concurrent.Memoized;

import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Data pertaining to a deployment to be done on a config server.
 * Accessor names must match the names in com.yahoo.vespa.config.server.session.PrepareParams.
 *
 * @author jonmv
 */
public class DeploymentData {

    private static final Logger log = Logger.getLogger(DeploymentData.class.getName());

    private final ApplicationId instance;
    private final ZoneId zone;
    private final Supplier<InputStream> applicationPackage;
    private final Version platform;
    private final Supplier<DeploymentEndpoints> endpoints;
    private final Optional<DockerImage> dockerImageRepo;
    private final Optional<AthenzDomain> athenzDomain;
    private final Supplier<Quota> quota;
    private final List<TenantSecretStore> tenantSecretStores;
    private final List<X509Certificate> operatorCertificates;
    private final Supplier<Optional<CloudAccount>> cloudAccount;
    private final Supplier<List<DataplaneTokenVersions>> dataPlaneTokens;
    private final boolean dryRun;

    public DeploymentData(ApplicationId instance, ZoneId zone, Supplier<InputStream> applicationPackage, Version platform,
                          Supplier<DeploymentEndpoints> endpoints,
                          Optional<DockerImage> dockerImageRepo,
                          Optional<AthenzDomain> athenzDomain,
                          Supplier<Quota> quota,
                          List<TenantSecretStore> tenantSecretStores,
                          List<X509Certificate> operatorCertificates,
                          Supplier<Optional<CloudAccount>> cloudAccount,
                          Supplier<List<DataplaneTokenVersions>> dataPlaneTokens,
                          boolean dryRun) {
        this.instance = requireNonNull(instance);
        this.zone = requireNonNull(zone);
        this.applicationPackage = requireNonNull(applicationPackage);
        this.platform = requireNonNull(platform);
        this.endpoints = wrap(requireNonNull(endpoints), Duration.ofSeconds(30), "deployment endpoints");
        this.dockerImageRepo = requireNonNull(dockerImageRepo);
        this.athenzDomain = athenzDomain;
        this.quota = wrap(requireNonNull(quota), Duration.ofSeconds(10), "quota");
        this.tenantSecretStores = List.copyOf(requireNonNull(tenantSecretStores));
        this.operatorCertificates = List.copyOf(requireNonNull(operatorCertificates));
        this.cloudAccount = wrap(requireNonNull(cloudAccount), Duration.ofSeconds(5), "cloud account");
        this.dataPlaneTokens = wrap(dataPlaneTokens, Duration.ofSeconds(5), "data plane tokens");
        this.dryRun = dryRun;
    }

    public ApplicationId instance() {
        return instance;
    }

    public ZoneId zone() {
        return zone;
    }

    public InputStream applicationPackage() {
        return applicationPackage.get();
    }

    public Version platform() {
        return platform;
    }

    public DeploymentEndpoints endpoints() {
        return endpoints.get();
    }

    public Optional<DockerImage> dockerImageRepo() {
        return dockerImageRepo;
    }

    public Optional<AthenzDomain> athenzDomain() {
        return athenzDomain;
    }

    public Quota quota() {
        return quota.get();
    }

    public List<TenantSecretStore> tenantSecretStores() {
        return tenantSecretStores;
    }

    public List<X509Certificate> operatorCertificates() {
        return operatorCertificates;
    }

    public Optional<CloudAccount> cloudAccount() {
        return cloudAccount.get();
    }

    public List<DataplaneTokenVersions> dataPlaneTokens() {
        return dataPlaneTokens.get();
    }

    public boolean isDryRun() {
        return dryRun;
    }

    private static <T> Supplier<T> wrap(Supplier<T> delegate, Duration timeout, String description) {
        return new TimingSupplier<>(new Memoized<>(delegate), timeout, description);
    }

    public static class TimingSupplier<T> implements Supplier<T> {

        private final Supplier<T> delegate;
        private final Duration timeout;
        private final String description;

        public TimingSupplier(Supplier<T> delegate, Duration timeout, String description) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.description = description;
        }

        @Override
        public T get() {
            long startNanos = System.nanoTime();
            Throwable thrown = null;
            try {
                return delegate.get();
            }
            catch (Throwable t) {
                thrown = t;
                throw t;
            }
            finally {
                long durationNanos = System.nanoTime() - startNanos;
                Level level = durationNanos > timeout.toNanos() ? Level.WARNING : Level.FINE;
                String thrownMessage = thrown == null ? "" : " with exception " + thrown;
                log.log(level, () -> "Getting " + description + " took " + Duration.ofNanos(durationNanos) + thrownMessage);
            }
        }

    }

}
