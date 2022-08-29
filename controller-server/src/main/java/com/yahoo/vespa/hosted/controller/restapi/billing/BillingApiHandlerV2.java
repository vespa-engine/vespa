// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.CollectionMethod;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Plan;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Quota;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author ogronnesby
 */
public class BillingApiHandlerV2 extends RestApiRequestHandler<BillingApiHandlerV2> {

    private static final Logger log = Logger.getLogger(BillingApiHandlerV2.class.getName());

    private static final String[] CSV_INVOICE_HEADER = new String[]{ "ID", "Tenant", "From", "To", "CpuHours", "MemoryHours", "DiskHours", "Cpu", "Memory", "Disk", "Additional" };

    private final ApplicationController applications;
    private final TenantController tenants;
    private final BillingController billing;
    private final PlanRegistry planRegistry;
    private final Clock clock;

    public BillingApiHandlerV2(ThreadedHttpRequestHandler.Context context, Controller controller) {
        super(context, BillingApiHandlerV2::createRestApi);
        this.applications = controller.applications();
        this.tenants = controller.tenants();
        this.billing = controller.serviceRegistry().billingController();
        this.planRegistry = controller.serviceRegistry().planRegistry();
        this.clock = controller.serviceRegistry().clock();
    }

    private static RestApi createRestApi(BillingApiHandlerV2 self) {
        return RestApi.builder()
                /*
                 * This is the API that is available to tenants to view their status
                 */
                .addRoute(RestApi.route("/billing/v2/tenant/{tenant}")
                        .get(self::tenant)
                        .patch(Slime.class, self::patchTenant))
                .addRoute(RestApi.route("/billing/v2/tenant/{tenant}/usage")
                        .get(self::tenantUsage))
                .addRoute(RestApi.route("/billing/v2/tenant/{tenant}/bill")
                        .get(self::tenantInvoiceList))
                .addRoute(RestApi.route("/billing/v2/tenant/{tenant}/bill/{invoice}")
                        .get(self::tenantInvoice))
                /*
                 * This is the API that is created for accountant role in Vespa Cloud
                 */
                .addRoute(RestApi.route("/billing/v2/accountant")
                        .get(self::accountant))
                .addRoute(RestApi.route("/billing/v2/accountant/preview/tenant/{tenant}")
                        .get(self::previewBill)
                        .post(Slime.class, self::createBill))
                .addRoute(RestApi.route("/billing/v2/accountant/plans")
                        .get(self::plans))
                .addExceptionMapper(RuntimeException.class, (c, e) -> ErrorResponses.logThrowing(c.request(), log, e))
                .build();
    }

    // ---------- TENANT API ----------

    private Slime tenant(RestApi.RequestContext requestContext) {
        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);

        var plan = planFor(tenant.name());
        var collectionMethod = billing.getCollectionMethod(tenant.name());

        var response = new Slime();
        var cursor = response.setObject();
        cursor.setString("tenant", tenant.name().value());

        toSlime(cursor.setObject("plan"), plan);
        cursor.setString("collection", collectionMethod.name());
        return response;
    }

    private Slime patchTenant(RestApi.RequestContext requestContext, Slime body) {
        var security = requestContext.attributes().get(SecurityContext.ATTRIBUTE_NAME)
                .map(SecurityContext.class::cast)
                .orElseThrow(() -> new RestApiException.Forbidden("Must be logged in"));

        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);

        var newPlan = body.get().field("plan");
        var newCollection = body.get().field("collection");

        if (newPlan.valid() && newPlan.type() == Type.STRING) {
            var planId = PlanId.from(newPlan.asString());
            var hasDeployments = tenantHasDeployments(tenant.name());
            var result = billing.setPlan(tenant.name(), planId, hasDeployments, false);
            if (! result.isSuccess()) {
                throw new RestApiException.Forbidden(result.getErrorMessage().get());
            }
        }

        if (newCollection.valid() && newCollection.type() == Type.STRING) {
            if (security.roles().contains(Role.hostedAccountant())) {
                var collection = CollectionMethod.valueOf(newCollection.asString());
                billing.setCollectionMethod(tenant.name(), collection);
            } else {
                throw new RestApiException.Forbidden("Only accountant can change billing method");
            }
        }

        var response = new Slime();
        var cursor = response.setObject();
        cursor.setString("tenant", tenant.name().value());
        toSlime(cursor.setObject("plan"), planFor(tenant.name()));
        cursor.setString("collection", billing.getCollectionMethod(tenant.name()).name());
        return response;
    }

    private Slime tenantInvoiceList(RestApi.RequestContext requestContext) {
        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);

        var slime = new Slime();
        invoicesSummaryToSlime(slime.setObject().setArray("invoices"), billing.getBillsForTenant(tenant.name()));
        return slime;
    }

    private HttpResponse tenantInvoice(RestApi.RequestContext requestContext) {
        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);
        var invoiceId = requestContext.pathParameters().getStringOrThrow("invoice");
        var format = requestContext.queryParameters().getString("format").orElse("json");

        var invoice = billing.getBillsForTenant(tenant.name()).stream()
                .filter(inv -> inv.id().value().equals(invoiceId))
                .findAny()
                .orElseThrow(RestApiException.NotFound::new);

        if (format.equals("json")) {
            var slime = new Slime();
            toSlime(slime.setObject(), invoice);
            return new SlimeJsonResponse(slime);
        }

        if (format.equals("csv")) {
            var csv = toCsv(invoice);
            return new CsvResponse(CSV_INVOICE_HEADER, csv);
        }

        throw new RestApiException.BadRequest("Unknown format: " + format);
    }

    private boolean tenantHasDeployments(TenantName tenant) {
        return applications.asList(tenant).stream()
                .flatMap(app -> app.instances().values().stream())
                .mapToLong(instance -> instance.deployments().size())
                .sum() > 0;
    }

    private Slime tenantUsage(RestApi.RequestContext requestContext) {
        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);
        var untilAt = untilParameter(requestContext);
        var usage = billing.createUncommittedBill(tenant.name(), untilAt);
        var slime = new Slime();
        usageToSlime(slime.setObject(), usage);
        return slime;
    }

    // --------- ACCOUNTANT API ----------

    private Slime accountant(RestApi.RequestContext requestContext) {
        var untilAt = untilParameter(requestContext);
        var usagePerTenant = billing.createUncommittedBills(untilAt);

        var response = new Slime();
        var tenantsResponse = response.setObject().setArray("tenants");

        tenants.asList().stream().sorted(Comparator.comparing(Tenant::name)).forEach(tenant -> {
            var usage = Optional.ofNullable(usagePerTenant.get(tenant.name()));
            var tenantResponse = tenantsResponse.addObject();
            tenantResponse.setString("tenant", tenant.name().value());
            toSlime(tenantResponse.setObject("plan"), planFor(tenant.name()));
            toSlime(tenantResponse.setObject("quota"), billing.getQuota(tenant.name()));
            tenantResponse.setString("collection", billing.getCollectionMethod(tenant.name()).name());
            tenantResponse.setString("lastBill", usage.map(Bill::getStartDate).map(DateTimeFormatter.ISO_DATE::format).orElse(null));
            tenantResponse.setString("unbilled", usage.map(Bill::sum).map(BigDecimal::toPlainString).orElse("0.00"));
        });

        return response;
    }

    private Slime previewBill(RestApi.RequestContext requestContext) {
        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);
        var untilAt = untilParameter(requestContext);

        var usage = billing.createUncommittedBill(tenant.name(), untilAt);

        var slime = new Slime();
        toSlime(slime.setObject(), usage);
        return slime;
    }

    private HttpResponse createBill(RestApi.RequestContext requestContext, Slime slime) {
        var body = slime.get();
        var security = requestContext.attributes().get(SecurityContext.ATTRIBUTE_NAME)
                .map(SecurityContext.class::cast)
                .orElseThrow(() -> new RestApiException.Forbidden("Must be logged in"));

        var tenantName = TenantName.from(requestContext.pathParameters().getStringOrThrow("tenant"));
        var tenant = tenants.require(tenantName, CloudTenant.class);

        var startAt = LocalDate.parse(getInspectorFieldOrThrow(body, "from")).atStartOfDay(ZoneOffset.UTC);
        var endAt = LocalDate.parse(getInspectorFieldOrThrow(body, "to")).plusDays(1).atStartOfDay(ZoneOffset.UTC);

        var invoiceId = billing.createBillForPeriod(tenant.name(), startAt, endAt, security.principal().getName());

        // TODO: Make a redirect to the bill itself
        return new MessageResponse("Created bill " + invoiceId.value());
    }

    private HttpResponse plans(RestApi.RequestContext ctx) {
        var slime = new Slime();
        var root = slime.setObject();
        var plans = root.setArray("plans");
        for (var plan : planRegistry.all()) {
            var p = plans.addObject();
            p.setString("id", plan.id().value());
            p.setString("name", plan.displayName());
        }
        return new SlimeJsonResponse(slime);
    }


    // --------- INVOICE RENDERING ----------

    private void invoicesSummaryToSlime(Cursor slime, List<Bill> bills) {
        bills.forEach(invoice -> invoiceSummaryToSlime(slime.addObject(), invoice));
    }

    private void invoiceSummaryToSlime(Cursor slime, Bill bill) {
        slime.setString("id", bill.id().value());
        slime.setString("from", bill.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("to", bill.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("total", bill.sum().toString());
        slime.setString("status", bill.status());
    }

    private void usageToSlime(Cursor slime, Bill bill) {
        slime.setString("from", bill.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("to", bill.getEndTime().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("total", bill.sum().toString());
        toSlime(slime.setArray("items"), bill.lineItems());
    }

    private void toSlime(Cursor slime, Bill bill) {
        slime.setString("id", bill.id().value());
        slime.setString("from", bill.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("to", bill.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        slime.setString("total", bill.sum().toString());
        slime.setString("status", bill.status());
        toSlime(slime.setArray("statusHistory"), bill.statusHistory());
        toSlime(slime.setArray("items"), bill.lineItems());
    }

    private void toSlime(Cursor slime, Bill.StatusHistory history) {
        history.getHistory().forEach((key, value) -> {
            var c = slime.addObject();
            c.setString("at", key.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            c.setString("status", value);
        });
    }

    private void toSlime(Cursor slime, List<Bill.LineItem> items) {
        items.forEach(item -> toSlime(slime.addObject(), item));
    }

    private void toSlime(Cursor slime, Bill.LineItem item) {
        slime.setString("id", item.id());
        slime.setString("description", item.description());
        slime.setString("amount",item.amount().toString());
        toSlime(slime.setObject("plan"), planRegistry.plan(item.plan()).orElseThrow(() -> new RuntimeException("No such plan: '" + item.plan() + "'")));

        item.applicationId().ifPresent(appId -> {
            slime.setString("application", appId.application().value());
            slime.setString("instance", appId.instance().value());
        });

        item.zoneId().ifPresent(z -> slime.setString("zone", z.value()));

        toSlime(slime.setObject("cpu"), item.getCpuHours(), item.getCpuCost());
        toSlime(slime.setObject("memory"), item.getMemoryHours(), item.getMemoryCost());
        toSlime(slime.setObject("disk"), item.getDiskHours(), item.getDiskCost());
    }

    private void toSlime(Cursor slime, Optional<BigDecimal> hours, Optional<BigDecimal> cost) {
        hours.ifPresent(h -> slime.setString("hours", h.toString()));
        cost.ifPresent(c -> slime.setString("cost", c.toString()));
    }

    private List<Object[]> toCsv(Bill bill) {
        return List.<Object[]>of(new Object[]{
                bill.id().value(), bill.tenant().value(),
                bill.getStartDate().format(DateTimeFormatter.ISO_DATE),
                bill.getEndDate().format(DateTimeFormatter.ISO_DATE),
                bill.sumCpuHours(), bill.sumMemoryHours(), bill.sumDiskHours(),
                bill.sumCpuCost(), bill.sumMemoryCost(), bill.sumDiskCost(),
                bill.sumAdditionalCost()
        });
    }

    // ---------- END INVOICE RENDERING ----------

    private LocalDate untilParameter(RestApi.RequestContext ctx) {
        return ctx.queryParameters().getString("until")
                .map(LocalDate::parse)
                .map(date -> date.plusDays(1))
                .orElseGet(this::tomorrow);
    }

    private LocalDate tomorrow() {
        return LocalDate.now(clock).plusDays(1);
    }

    private static String getInspectorFieldOrThrow(Inspector inspector, String field) {
        if (!inspector.field(field).valid())
            throw new RestApiException.BadRequest("Field " + field + " cannot be null");
        return inspector.field(field).asString();
    }

    private void toSlime(Cursor cursor, Plan plan) {
        cursor.setString("id", plan.id().value());
        cursor.setString("name", plan.displayName());
    }

    private void toSlime(Cursor cursor, Quota quota) {
        cursor.setDouble("budget", quota.budget().map(BigDecimal::doubleValue).orElse(-1.0));
    }

    private Plan planFor(TenantName tenant) {
        var planId = billing.getPlan(tenant);
        return planRegistry.plan(planId)
                .orElseThrow(() -> new RuntimeException("No such plan: '" + planId + "'"));
    }
}
