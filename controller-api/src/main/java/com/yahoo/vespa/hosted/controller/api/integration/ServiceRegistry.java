// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration;

import com.yahoo.vespa.hosted.controller.api.integration.aws.AwsEventFetcher;
import com.yahoo.vespa.hosted.controller.api.integration.certificates.ApplicationCertificateProvider;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ArtifactRepository;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.entity.EntityService;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Billing;
import com.yahoo.vespa.hosted.controller.api.integration.organization.ContactRetriever;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.IssueHandler;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.resource.CostReportConsumer;
import com.yahoo.vespa.hosted.controller.api.integration.resource.MeteringClient;
import com.yahoo.vespa.hosted.controller.api.integration.resource.TenantCost;
import com.yahoo.vespa.hosted.controller.api.integration.routing.GlobalRoutingService;
import com.yahoo.vespa.hosted.controller.api.integration.routing.RoutingGenerator;

import java.time.Clock;

/**
 * This provides access to all service dependencies of the controller. Implementations of this are responsible for
 * constructing and configuring service implementations suitable for use by the controller.
 *
 * @author mpolden
 */
// TODO(mpolden): Access all services through this
public interface ServiceRegistry {

    ConfigServer configServer();

    Clock clock();

    NameService nameService();

    GlobalRoutingService globalRoutingService();

    RoutingGenerator routingGenerator();

    Mailer mailer();

    ApplicationCertificateProvider applicationCertificateProvider();

    MeteringClient meteringService();

    ContactRetriever contactRetriever();

    IssueHandler issueHandler();

    OwnershipIssues ownershipIssues();

    DeploymentIssues deploymentIssues();

    EntityService entityService();

    CostReportConsumer costReportConsumer();

    Billing billingService();

    AwsEventFetcher eventFetcherService();

    ArtifactRepository artifactRepository();

    TesterCloud testerCloud();

    ApplicationStore applicationStore();

    RunDataStore runDataStore();

    // TODO: No longer used. Remove this once untangled from test code
    BuildService buildService();

    TenantCost tenantCost();

}
