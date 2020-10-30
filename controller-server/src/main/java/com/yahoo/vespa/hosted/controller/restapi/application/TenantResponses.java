package com.yahoo.vespa.hosted.controller.restapi.application;

import com.google.common.collect.ImmutableSet;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.KeyUtils;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.NotExistsException;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.application.QuotaUsage;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.security.AccessControlRequests;
import com.yahoo.vespa.hosted.controller.tenant.AthenzTenant;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoAddress;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoBillingContact;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public class TenantResponses {

    private final Controller controller;
    private final AccessControlRequests accessControlRequests;

    TenantResponses(Controller controller, AccessControlRequests accessControlRequests) {
        this.controller = controller;
        this.accessControlRequests = accessControlRequests;
    }

    private static boolean recurseOverDeployments(HttpRequest request) {
        return ImmutableSet.of("all", "true", "deployment").contains(request.getProperty("recursive"));
    }

    private static boolean recurseOverApplications(HttpRequest request) {
        return recurseOverDeployments(request) || "application".equals(request.getProperty("recursive"));
    }

    private static boolean recurseOverTenants(HttpRequest request) {
        return recurseOverApplications(request) || "tenant".equals(request.getProperty("recursive"));
    }

    HttpResponse root(HttpRequest request) {
        return recurseOverTenants(request)
                ? recursiveRoot(request)
                : new ResourceResponse(request, "tenant");
    }

    HttpResponse recursiveRoot(HttpRequest request) {
        Slime slime = new Slime();
        Cursor tenantArray = slime.setArray();
        for (Tenant tenant : controller.tenants().asList())
            toSlime(tenantArray.addObject(), tenant, request);
        return new SlimeJsonResponse(slime);
    }

    HttpResponse tenants(HttpRequest request) {
        Slime slime = new Slime();
        Cursor response = slime.setArray();
        for (Tenant tenant : controller.tenants().asList())
            tenantInTenantsListToSlime(tenant, request.getUri(), response.addObject());
        return new SlimeJsonResponse(slime);
    }

    HttpResponse tenant(String tenantName, HttpRequest request) {
        return controller.tenants().get(TenantName.from(tenantName))
                .map(tenant -> tenant(tenant, request))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist"));
    }

    private HttpResponse tenant(Tenant tenant, HttpRequest request) {
        Slime slime = new Slime();
        toSlime(slime.setObject(), tenant, request);
        return new SlimeJsonResponse(slime);
    }

    HttpResponse tenantInfo(String tenantName) {
        return controller.tenants().get(TenantName.from(tenantName))
                .filter(tenant -> tenant instanceof CloudTenant)
                .map(tenant -> (CloudTenant)tenant)
                .map(tenant -> tenantInfo(tenant.info()))
                .orElseGet(() -> ErrorResponse.notFoundError("Tenant '" + tenantName + "' does not exist"));
    }

    private HttpResponse tenantInfo(TenantInfo info) {
        Slime slime = new Slime();
        Cursor infoCursor = slime.setObject();
        if (!info.isEmpty()) {
            infoCursor.setString("name", info.name());
            infoCursor.setString("email", info.email());
            infoCursor.setString("website", info.website());
            infoCursor.setString("invoiceEmail", info.invoiceEmail());
            infoCursor.setString("contactName", info.contactName());
            infoCursor.setString("contactEmail", info.contactEmail());
            toSlime(info.address(), infoCursor);
            toSlime(info.billingContact(), infoCursor);
        }

        return new SlimeJsonResponse(slime);
    }

    private void toSlime(TenantInfoAddress address, Cursor parentCursor) {
        if (address.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("address");
        addressCursor.setString("addressLines", address.addressLines());
        addressCursor.setString("postalCodeOrZip", address.postalCodeOrZip());
        addressCursor.setString("city", address.city());
        addressCursor.setString("stateRegionProvince", address.stateRegionProvince());
        addressCursor.setString("country", address.country());
    }

    private void toSlime(TenantInfoBillingContact billingContact, Cursor parentCursor) {
        if (billingContact.isEmpty()) return;

        Cursor addressCursor = parentCursor.setObject("billingContact");
        addressCursor.setString("name", billingContact.name());
        addressCursor.setString("email", billingContact.email());
        addressCursor.setString("phone", billingContact.phone());
        toSlime(billingContact.address(), addressCursor);
    }

    // A tenant has different content when in a list ... antipattern, but not solvable before application/v5
    private void tenantInTenantsListToSlime(Tenant tenant, URI requestURI, Cursor object) {
        object.setString("tenant", tenant.name().value());
        Cursor metaData = object.setObject("metaData");
        metaData.setString("type", tenantType(tenant));
        switch (tenant.type()) {
            case athenz:
                AthenzTenant athenzTenant = (AthenzTenant) tenant;
                metaData.setString("athensDomain", athenzTenant.domain().getName());
                metaData.setString("property", athenzTenant.property().id());
                break;
            case cloud: break;
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        object.setString("url", ApplicationApiHandler.withPath("/application/v4/tenant/" + tenant.name().value(), requestURI).toString());
    }

    private void toSlime(Cursor object, Tenant tenant, HttpRequest request) {
        object.setString("tenant", tenant.name().value());
        object.setString("type", tenantType(tenant));
        List<Application> applications = controller.applications().asList(tenant.name());
        switch (tenant.type()) {
            case athenz:
                AthenzTenant athenzTenant = (AthenzTenant) tenant;
                object.setString("athensDomain", athenzTenant.domain().getName());
                object.setString("property", athenzTenant.property().id());
                athenzTenant.propertyId().ifPresent(id -> object.setString("propertyId", id.toString()));
                athenzTenant.contact().ifPresent(c -> {
                    object.setString("propertyUrl", c.propertyUrl().toString());
                    object.setString("contactsUrl", c.url().toString());
                    object.setString("issueCreationUrl", c.issueTrackerUrl().toString());
                    Cursor contactsArray = object.setArray("contacts");
                    c.persons().forEach(persons -> {
                        Cursor personArray = contactsArray.addArray();
                        persons.forEach(personArray::addString);
                    });
                });
                break;
            case cloud: {
                CloudTenant cloudTenant = (CloudTenant) tenant;

                cloudTenant.creator().ifPresent(creator -> object.setString("creator", creator.getName()));
                Cursor pemDeveloperKeysArray = object.setArray("pemDeveloperKeys");
                cloudTenant.developerKeys().forEach((key, user) -> {
                    Cursor keyObject = pemDeveloperKeysArray.addObject();
                    keyObject.setString("key", KeyUtils.toPem(key));
                    keyObject.setString("user", user.getName());
                });

                var tenantQuota = controller.serviceRegistry().billingController().getQuota(tenant.name());
                var usedQuota = applications.stream()
                        .map(com.yahoo.vespa.hosted.controller.Application::quotaUsage)
                        .reduce(QuotaUsage.none, QuotaUsage::add);

                toSlime(tenantQuota, usedQuota, object.setObject("quota"));

                break;
            }
            default: throw new IllegalArgumentException("Unexpected tenant type '" + tenant.type() + "'.");
        }
        // TODO jonmv: This should list applications, not instances.
        Cursor applicationArray = object.setArray("applications");
        for (com.yahoo.vespa.hosted.controller.Application application : applications) {
            DeploymentStatus status = controller.jobController().deploymentStatus(application);
            for (Instance instance : ApplicationApiHandler.showOnlyProductionInstances(request) ? application.productionInstances().values()
                    : application.instances().values())
                if (recurseOverApplications(request))
                    ApplicationApiHandler.toSlime(applicationArray.addObject(), instance, status, request, controller);
                else
                    ApplicationApiHandler.toSlime(instance.id(), applicationArray.addObject(), request);
        }
    }

    private Tenant getTenantOrThrow(String tenantName) {
        return controller.tenants().get(tenantName)
                .orElseThrow(() -> new NotExistsException(new TenantId(tenantName)));
    }

    HttpResponse updateTenant(String tenantName, HttpRequest request) {
        getTenantOrThrow(tenantName);
        TenantName tenant = TenantName.from(tenantName);
        Inspector requestObject = ApplicationApiHandler.toSlime(request.getData()).get();
        controller.tenants().update(accessControlRequests.specification(tenant, requestObject),
                accessControlRequests.credentials(tenant, requestObject, request.getJDiscRequest()));
        return tenant(controller.tenants().require(TenantName.from(tenantName)), request);
    }

    HttpResponse createTenant(String tenantName, HttpRequest request) {
        TenantName tenant = TenantName.from(tenantName);
        Inspector requestObject = ApplicationApiHandler.toSlime(request.getData()).get();
        controller.tenants().create(accessControlRequests.specification(tenant, requestObject),
                accessControlRequests.credentials(tenant, requestObject, request.getJDiscRequest()));
        return tenant(controller.tenants().require(TenantName.from(tenantName)), request);
    }

    HttpResponse deleteTenant(String tenantName, HttpRequest request) {
        Optional<Tenant> tenant = controller.tenants().get(tenantName);
        if (tenant.isEmpty())
            return ErrorResponse.notFoundError("Could not delete tenant '" + tenantName + "': Tenant not found");

        controller.tenants().delete(tenant.get().name(),
                accessControlRequests.credentials(tenant.get().name(),
                        ApplicationApiHandler.toSlime(request.getData()).get(),
                        request.getJDiscRequest()));

        // TODO: Change to a message response saying the tenant was deleted
        return tenant(tenant.get(), request);
    }

    private static String tenantType(Tenant tenant) {
        switch (tenant.type()) {
            case athenz: return "ATHENS";
            case cloud: return "CLOUD";
            default: throw new IllegalArgumentException("Unknown tenant type: " + tenant.getClass().getSimpleName());
        }
    }

    private void toSlime(Quota quota, QuotaUsage usage, Cursor object) {
        quota.budget().ifPresentOrElse(
                budget -> object.setDouble("budget", budget.doubleValue()),
                () -> object.setNix("budget")
        );
        object.setDouble("budgetUsed", usage.rate());

        // TODO: Retire when we no longer use maxClusterSize as a meaningful limit
        quota.maxClusterSize().ifPresent(maxClusterSize -> object.setLong("clusterSize", maxClusterSize));
    }
}
