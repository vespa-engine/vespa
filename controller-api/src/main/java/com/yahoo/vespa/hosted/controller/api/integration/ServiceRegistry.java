// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.identifiers.ControllerVersion;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.api.integration.artifact.ArtifactRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AccessControlService;
import com.yahoo.vespa.hosted.controller.api.integration.aws.ResourceTagger;
import com.yahoo.vespa.hosted.controller.api.integration.aws.RoleService;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.EndpointCertificateValidator;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.horizon.HorizonClient;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.SystemMonitor;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClient;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.GcpSecretStore;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretService;
import com.yahoo.vespa.hosted.controller.api.integration.user.RoleMaintainer;
import com.yahoo.vespa.hosted.controller.api.integration.vcmr.ChangeRequestClient;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;

import java.time.Clock;
import java.util.Optional;

/**
 * This provides access to all service dependencies of the controller. Implementations of this are responsible for
 * constructing and configuring service implementations suitable for use by the controller.
 *
 * @author mpolden
 */
public interface ServiceRegistry {

    ConfigServer configServer();

    default Clock clock() { return Clock.systemUTC(); }

    default ControllerVersion controllerVersion() { return ControllerVersion.CURRENT; }

    default HostName getHostname() { return HostName.of(com.yahoo.net.HostName.getLocalhost()); }

    NameService nameService();

    Mailer mailer();

    EndpointCertificateProvider endpointCertificateProvider();

    EndpointCertificateValidator endpointCertificateValidator();

    ContactRetriever contactRetriever();

    IssueHandler issueHandler();

    OwnershipIssues ownershipIssues();

    DeploymentIssues deploymentIssues();

    EntityService entityService();

    CostReportConsumer costReportConsumer();

    ArtifactRepository artifactRepository();

    TesterCloud testerCloud();

    ApplicationStore applicationStore();

    RunDataStore runDataStore();

    ZoneRegistry zoneRegistry();

    ResourceTagger resourceTagger();

    RoleService roleService();

    SystemMonitor systemMonitor();

    BillingController billingController();

    ResourceDatabaseClient resourceDatabase();

    BillingDatabaseClient billingDatabase();

    Optional<? extends ArtifactRegistry> artifactRegistry(CloudName cloudName);

    TenantSecretService tenantSecretService();

    ArchiveService archiveService();

    ChangeRequestClient changeRequestClient();

    AccessControlService accessControlService();

    HorizonClient horizonClient();

    PlanRegistry planRegistry();

    RoleMaintainer roleMaintainer();

    GcpSecretStore gcpSecretStore();
}
