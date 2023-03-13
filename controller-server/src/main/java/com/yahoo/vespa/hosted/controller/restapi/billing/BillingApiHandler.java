// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.JacksonJsonResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.restapi.StringResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.CollectionMethod;
import com.yahoo.vespa.hosted.controller.api.integration.billing.InstrumentOwner;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PaymentInstrument;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistry;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.api.role.SecurityContext;
import com.yahoo.vespa.hosted.controller.restapi.ErrorResponses;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.yolean.Exceptions;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * @author andreer
 * @author olaa
 */
public class BillingApiHandler extends ThreadedHttpRequestHandler {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BillingController billingController;
    private final ApplicationController applicationController;
    private final TenantController tenantController;
    private final PlanRegistry planRegistry;

    public BillingApiHandler(Executor executor,
                             Controller controller) {
        super(executor);
        this.billingController = controller.serviceRegistry().billingController();
        this.planRegistry = controller.serviceRegistry().planRegistry();
        this.applicationController = controller.applications();
        this.tenantController = controller.tenants();
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            Optional<String> userId = Optional.ofNullable(request.getJDiscRequest().getUserPrincipal()).map(Principal::getName);
            if (userId.isEmpty())
                return ErrorResponse.unauthorized("Must be authenticated to use this API");

            Path path = new Path(request.getUri());
            return switch (request.getMethod()) {
                case GET -> handleGET(request, path, userId.get());
                case PATCH -> handlePATCH(request, path, userId.get());
                case DELETE -> handleDELETE(path, userId.get());
                case POST -> handlePOST(path, request, userId.get());
                default -> ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            };
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (Exception e) {
            return ErrorResponses.logThrowing(request, log, e);
        }
    }

    private HttpResponse handleGET(HttpRequest request, Path path, String userId) {
        if (path.matches("/billing/v1/tenant/{tenant}/token")) return getToken(path.get("tenant"), userId);
        if (path.matches("/billing/v1/tenant/{tenant}/instrument")) return getInstruments(path.get("tenant"), userId);
        if (path.matches("/billing/v1/tenant/{tenant}/billing")) return getBilling(path.get("tenant"), request.getProperty("until"));
        if (path.matches("/billing/v1/tenant/{tenant}/plan")) return getPlan(path.get("tenant"));
        if (path.matches("/billing/v1/billing")) return getBillingAllTenants(request.getProperty("until"));
        if (path.matches("/billing/v1/invoice/export")) return getAllBills();
        if (path.matches("/billing/v1/invoice/tenant/{tenant}/line-item")) return getLineItems(path.get("tenant"));
        if (path.matches("/billing/v1/plans")) return getPlans();
        return ErrorResponse.notFoundError("Nothing at " + path);
    }

    private HttpResponse getAllBills() {
        var bills = billingController.getBills();
        var headers = new String[]{ "ID", "Tenant", "From", "To", "CpuHours", "MemoryHours", "DiskHours", "Cpu", "Memory", "Disk", "Additional" };
        var rows = bills.stream()
                .map(bill -> {
                    return new Object[] {
                            bill.id().value(), bill.tenant().value(),
                            bill.getStartDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            bill.getEndDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                            bill.sumCpuHours(), bill.sumMemoryHours(), bill.sumDiskHours(),
                            bill.sumCpuCost(), bill.sumMemoryCost(), bill.sumDiskCost(),
                            bill.sumAdditionalCost()
                    };
                })
                .toList();
        return new CsvResponse(headers, rows);
    }

    private HttpResponse handlePATCH(HttpRequest request, Path path, String userId) {
        if (path.matches("/billing/v1/tenant/{tenant}/instrument")) return patchActiveInstrument(request, path.get("tenant"), userId);
        if (path.matches("/billing/v1/tenant/{tenant}/plan")) return patchPlan(request, path.get("tenant"));
        if (path.matches("/billing/v1/tenant/{tenant}/collection")) return patchCollectionMethod(request, path.get("tenant"));
        return ErrorResponse.notFoundError("Nothing at " + path);

    }

    private HttpResponse handleDELETE(Path path, String userId) {
        if (path.matches("/billing/v1/tenant/{tenant}/instrument/{instrument}")) return deleteInstrument(path.get("tenant"), userId, path.get("instrument"));
        if (path.matches("/billing/v1/invoice/line-item/{line-item-id}")) return deleteLineItem(path.get("line-item-id"));
        return ErrorResponse.notFoundError("Nothing at " + path);

    }

    private HttpResponse handlePOST(Path path, HttpRequest request, String userId) {
        if (path.matches("/billing/v1/invoice")) return createBill(request, userId);
        if (path.matches("/billing/v1/invoice/{invoice-id}/status")) return setBillStatus(request, path.get("invoice-id"), userId);
        if (path.matches("/billing/v1/invoice/tenant/{tenant}/line-item")) return addLineItem(request, path.get("tenant"), userId);
        return ErrorResponse.notFoundError("Nothing at " + path);

    }

    private HttpResponse getPlan(String tenant) {
        var plan = billingController.getPlan(TenantName.from(tenant));
        var slime = new Slime();
        var root = slime.setObject();
        root.setString("tenant", tenant);
        root.setString("plan", plan.value());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse patchPlan(HttpRequest request, String tenant) {
        var tenantName = TenantName.from(tenant);
        var slime = inspectorOrThrow(request);
        var planId = PlanId.from(slime.field("plan").asString());
        var roles = requestRoles(request);
        var isAccountant = roles.contains(Role.hostedAccountant());

        var hasDeployments = hasDeployments(tenantName);
        var result = billingController.setPlan(tenantName, planId, hasDeployments, isAccountant);

        if (result.isSuccess())
            return new StringResponse("Plan: " + planId.value());

        return ErrorResponse.forbidden(result.getErrorMessage().orElse("Invalid plan change"));
    }

    private HttpResponse patchCollectionMethod(HttpRequest request, String tenant) {
        var tenantName = TenantName.from(tenant);
        var slime = inspectorOrThrow(request);
        var newMethod = slime.field("collection").valid() ?
                slime.field("collection").asString().toUpperCase() :
                slime.field("collectionMethod").asString().toUpperCase();
        if (newMethod.isEmpty()) return ErrorResponse.badRequest("No collection method specified");

        try {
            var result = billingController.setCollectionMethod(tenantName, CollectionMethod.valueOf(newMethod));
            if (result.isSuccess())
                return new StringResponse("Collection method updated to " + newMethod);

            return ErrorResponse.forbidden(result.getErrorMessage().orElse("Invalid collection method change"));
        } catch (IllegalArgumentException iea){
            return ErrorResponse.badRequest("Invalid collection method: " + newMethod);
        }
    }

    private HttpResponse getBillingAllTenants(String until) {
        try {
            var untilDate = untilParameter(until);
            var uncommittedBills = billingController.createUncommittedBills(untilDate);

            var slime = new Slime();
            var root = slime.setObject();
            root.setString("until", untilDate.format(DateTimeFormatter.ISO_DATE));
            var tenants = root.setArray("tenants");

            tenantController.asList().stream().sorted(Comparator.comparing(Tenant::name)).forEach(tenant -> {
                var bill = uncommittedBills.get(tenant.name());
                var tc = tenants.addObject();
                tc.setString("tenant", tenant.name().value());
                getPlanForTenant(tc, tenant.name());
                getCollectionForTenant(tc, tenant.name());
                renderCurrentUsage(tc.setObject("current"), bill);
                renderAdditionalItems(tc.setObject("additional").setArray("items"), billingController.getUnusedLineItems(tenant.name()));

                billingController.getDefaultInstrument(tenant.name()).ifPresent(card ->
                        renderInstrument(tc.setObject("payment"), card)
                );
            });

            return new SlimeJsonResponse(slime);
        } catch (DateTimeParseException e) {
            return ErrorResponse.badRequest("Could not parse date: " + until);
        }
    }

    private void getCollectionForTenant(Cursor tc, TenantName tenant) {
        var collection = billingController.getCollectionMethod(tenant);
        tc.setString("collection", collection.name());
    }

    private HttpResponse addLineItem(HttpRequest request, String tenant, String userId) {
        Inspector inspector = inspectorOrThrow(request);

        Optional<Bill.Id> billId = SlimeUtils.optionalString(inspector.field("billId")).map(Bill.Id::of);

        billingController.addLineItem(
                TenantName.from(tenant),
                getInspectorFieldOrThrow(inspector, "description"),
                new BigDecimal(getInspectorFieldOrThrow(inspector, "amount")),
                billId,
                userId);

        return new MessageResponse("Added line item for tenant " + tenant);
    }

    private HttpResponse setBillStatus(HttpRequest request, String billId, String userId) {
        Inspector inspector = inspectorOrThrow(request);
        String status = getInspectorFieldOrThrow(inspector, "status");
        billingController.updateBillStatus(Bill.Id.of(billId), userId, status);
        return new MessageResponse("Updated status of invoice " + billId);
    }

    private HttpResponse createBill(HttpRequest request, String userId) {
        Inspector inspector = inspectorOrThrow(request);
        TenantName tenantName = TenantName.from(getInspectorFieldOrThrow(inspector, "tenant"));

        LocalDate startDate = LocalDate.parse(getInspectorFieldOrThrow(inspector, "startTime"));
        LocalDate endDate = LocalDate.parse(getInspectorFieldOrThrow(inspector, "endTime"));

        var billId = billingController.createBillForPeriod(tenantName, startDate, endDate, userId);

        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("message", "Created invoice with ID " + billId.value());
        root.setString("id", billId.value());
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse getInstruments(String tenant, String userId) {
        var instrumentListResponse = billingController.listInstruments(TenantName.from(tenant), userId);
        return new JacksonJsonResponse<>(200, instrumentListResponse);
    }

    private HttpResponse getToken(String tenant, String userId) {
        return new StringResponse(billingController.createClientToken(tenant, userId));
    }

    private HttpResponse getBilling(String tenant, String until) {
        try {
            var untilDate = untilParameter(until);
            var tenantId = TenantName.from(tenant);
            var slimeResponse = new Slime();
            var root = slimeResponse.setObject();

            root.setString("until", untilDate.format(DateTimeFormatter.ISO_DATE));

            getPlanForTenant(root, tenantId);
            renderCurrentUsage(root.setObject("current"), getCurrentUsageForTenant(tenantId, untilDate));
            renderAdditionalItems(root.setObject("additional").setArray("items"), billingController.getUnusedLineItems(tenantId));
            renderBills(root.setArray("bills"), getBillsForTenant(tenantId));

            billingController.getDefaultInstrument(tenantId).ifPresent( card ->
                renderInstrument(root.setObject("payment"), card)
            );

            root.setString("collection", billingController.getCollectionMethod(tenantId).name());
            return new SlimeJsonResponse(slimeResponse);
        } catch (DateTimeParseException e) {
            return ErrorResponse.badRequest("Could not parse date: " + until);
        }
    }

    private HttpResponse getPlans() {
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

    private HttpResponse getLineItems(String tenant) {
        var slimeResponse = new Slime();
        var root = slimeResponse.setObject();
        var lineItems = root.setArray("lineItems");

        billingController.getUnusedLineItems(TenantName.from(tenant))
                .forEach(lineItem -> {
                    var itemCursor = lineItems.addObject();
                    renderLineItemToCursor(itemCursor, lineItem);
                });

        return new SlimeJsonResponse(slimeResponse);
    }

    private void getPlanForTenant(Cursor cursor, TenantName tenant) {
        PlanId plan = billingController.getPlan(tenant);
        cursor.setString("plan", plan.value());
        cursor.setString("planName", billingController.getPlanDisplayName(plan));
    }

    private void renderInstrument(Cursor cursor, PaymentInstrument instrument) {
        cursor.setString("pi-id", instrument.getId());
        cursor.setString("type", instrument.getType());
        cursor.setString("brand", instrument.getBrand());
        cursor.setString("endingWith", instrument.getEndingWith());
        cursor.setString("expiryDate", instrument.getExpiryDate());
        cursor.setString("displayText", instrument.getDisplayText());
        cursor.setString("nameOnCard", instrument.getNameOnCard());
        cursor.setString("addressLine1", instrument.getAddressLine1());
        cursor.setString("addressLine2", instrument.getAddressLine2());
        cursor.setString("zip", instrument.getZip());
        cursor.setString("city", instrument.getCity());
        cursor.setString("state", instrument.getState());
        cursor.setString("country", instrument.getCountry());

    }

    private void renderCurrentUsage(Cursor cursor, Bill currentUsage) {
        if (currentUsage == null) return;
        cursor.setString("amount", currentUsage.sum().toPlainString());
        cursor.setString("status", "accrued");
        cursor.setString("from", currentUsage.getStartDate().format(DATE_TIME_FORMATTER));
        var itemsCursor = cursor.setArray("items");
        currentUsage.lineItems().forEach(lineItem -> {
            var itemCursor = itemsCursor.addObject();
            renderLineItemToCursor(itemCursor, lineItem);
        });
    }

    private void renderAdditionalItems(Cursor cursor, List<Bill.LineItem> items) {
        items.forEach(item -> {
            renderLineItemToCursor(cursor.addObject(), item);
        });
    }

    private Bill getCurrentUsageForTenant(TenantName tenant, LocalDate until) {
        return billingController.createUncommittedBill(tenant, until);
    }

    private List<Bill> getBillsForTenant(TenantName tenant) {
        return billingController.getBillsForTenant(tenant);
    }

    private void renderBills(Cursor cursor, List<Bill> bills) {
        bills.forEach(bill -> {
            var billCursor = cursor.addObject();
            renderBillToCursor(billCursor, bill);
        });
    }

    private void renderBillToCursor(Cursor billCursor, Bill bill) {
        billCursor.setString("id", bill.id().value());
        billCursor.setString("from", bill.getStartDate().format(DATE_TIME_FORMATTER));
        billCursor.setString("to", bill.getEndDate().format(DATE_TIME_FORMATTER));

        billCursor.setString("amount", bill.sum().toString());
        billCursor.setString("status", bill.status());
        var statusCursor = billCursor.setArray("statusHistory");
        renderStatusHistory(statusCursor, bill.statusHistory());


        var lineItemsCursor = billCursor.setArray("items");
        bill.lineItems().forEach(lineItem -> {
            var itemCursor = lineItemsCursor.addObject();
            renderLineItemToCursor(itemCursor, lineItem);
        });
    }

    private void renderStatusHistory(Cursor cursor, Bill.StatusHistory statusHistory) {
        statusHistory.getHistory()
                .entrySet()
                .stream()
                .forEach(entry -> {
                    var c = cursor.addObject();
                    c.setString("at", entry.getKey().format(DATE_TIME_FORMATTER));
                    c.setString("status", entry.getValue());
                });
    }

    private void renderLineItemToCursor(Cursor cursor, Bill.LineItem lineItem) {
        cursor.setString("id", lineItem.id());
        cursor.setString("description", lineItem.description());
        cursor.setString("amount", lineItem.amount().toString());
        cursor.setString("plan", lineItem.plan());
        cursor.setString("planName", billingController.getPlanDisplayName(PlanId.from(lineItem.plan())));

        lineItem.applicationId().ifPresent(appId -> {
            cursor.setString("application", appId.application().value());
            cursor.setString("instance", appId.instance().value());
        });
        lineItem.zoneId().ifPresent(zoneId ->
            cursor.setString("zone", zoneId.value())
        );

        lineItem.getArchitecture().ifPresent(architecture -> {
            cursor.setString("architecture", architecture.name());
        });

        cursor.setLong("majorVersion", lineItem.getMajorVersion());

        lineItem.getCpuHours().ifPresent(cpuHours ->
                cursor.setString("cpuHours", cpuHours.toString())
        );
        lineItem.getMemoryHours().ifPresent(memoryHours ->
                cursor.setString("memoryHours", memoryHours.toString())
        );
        lineItem.getDiskHours().ifPresent(diskHours ->
                cursor.setString("diskHours", diskHours.toString())
        );
        lineItem.getGpuHours().ifPresent(gpuHours ->
                cursor.setString("gpuHours", gpuHours.toString())
        );
        lineItem.getCpuCost().ifPresent(cpuCost ->
                cursor.setString("cpuCost", cpuCost.toString())
        );
        lineItem.getMemoryCost().ifPresent(memoryCost ->
                cursor.setString("memoryCost", memoryCost.toString())
        );
        lineItem.getDiskCost().ifPresent(diskCost ->
                cursor.setString("diskCost", diskCost.toString())
        );
        lineItem.getGpuCost().ifPresent(gpuCost ->
                cursor.setString("gpuCost", gpuCost.toString())
        );
    }

    private HttpResponse deleteInstrument(String tenant, String userId, String instrument) {
        if (billingController.deleteInstrument(TenantName.from(tenant), userId, instrument)) {
            return new StringResponse("OK");
        } else {
            return ErrorResponse.forbidden("Cannot delete payment instrument you don't own");
        }
    }

    private HttpResponse deleteLineItem(String lineItemId) {
        billingController.deleteLineItem(lineItemId);
        return new MessageResponse("Succesfully deleted line item " + lineItemId);
    }

    private HttpResponse patchActiveInstrument(HttpRequest request, String tenant, String userId) {
        var inspector = inspectorOrThrow(request);
        String instrumentId = getInspectorFieldOrThrow(inspector, "active");
        InstrumentOwner paymentInstrument = new InstrumentOwner(TenantName.from(tenant), userId, instrumentId, true);
        boolean success = billingController.setActivePaymentInstrument(paymentInstrument);
        return success ? new StringResponse("OK") : ErrorResponse.internalServerError("Failed to patch active instrument");
    }

    private Inspector inspectorOrThrow(HttpRequest request) {
        try {
            return SlimeUtils.jsonToSlime(request.getData().readAllBytes()).get();
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse request body");
        }
    }

    private static String getInspectorFieldOrThrow(Inspector inspector, String field) {
        if (!inspector.field(field).valid())
            throw new IllegalArgumentException("Field " + field + " cannot be null");
        return inspector.field(field).asString();
    }

    private LocalDate untilParameter(String until) {
        if (until == null || until.isEmpty() || until.isBlank())
            return LocalDate.now();
        return LocalDate.parse(until);
    }

    private boolean hasDeployments(TenantName tenantName) {
        return applicationController.asList(tenantName)
                .stream()
                .flatMap(app -> app.instances().values()
                        .stream()
                        .flatMap(instance -> instance.deployments().values().stream())
                )
                .count() > 0;
    }

    private static Set<Role> requestRoles(HttpRequest request) {
        return Optional.ofNullable(request.getJDiscRequest().context().get(SecurityContext.ATTRIBUTE_NAME))
                .filter(SecurityContext.class::isInstance)
                .map(SecurityContext.class::cast)
                .map(SecurityContext::roles)
                .orElseThrow(() -> new IllegalArgumentException("Attribute '" + SecurityContext.ATTRIBUTE_NAME + "' was not set on request"));
    }

}
