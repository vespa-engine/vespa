package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.billing.CollectionMethod;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Invoice;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.yahoo.application.container.handler.Request.Method.*;
import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class BillingApiHandlerTest extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/billing/responses/";
    private static final TenantName tenant = TenantName.from("tenant1");
    private static final TenantName tenant2 = TenantName.from("tenant2");
    private static final Set<Role> tenantRole = Set.of(Role.administrator(tenant));
    private static final Set<Role> financeAdmin = Set.of(Role.hostedAccountant());
    private MockBillingController billingController;

    private ContainerTester tester;

    @Before
    public void setup() {
        tester = new ContainerTester(container, responseFiles);
        billingController = (MockBillingController) tester.serviceRegistry().billingController();
    }

    @Override
    protected SystemName system() {
        return SystemName.PublicCd;
    }

    @Override
    protected String variablePartXml() {
        return "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControlRequests'/>\n" +
                "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControl'/>\n" +

                "  <handler id='com.yahoo.vespa.hosted.controller.restapi.billing.BillingApiHandler'>\n" +
                "    <binding>http://*/billing/v1/*</binding>\n" +
                "  </handler>\n" +

                "  <http>\n" +
                "    <server id='default' port='8080' />\n" +
                "    <filtering>\n" +
                "      <request-chain id='default'>\n" +
                "        <filter id='com.yahoo.vespa.hosted.controller.restapi.filter.ControllerAuthorizationFilter'/>\n" +
                "        <binding>http://*/*</binding>\n" +
                "      </request-chain>\n" +
                "    </filtering>\n" +
                "  </http>\n";
    }

    @Test
    public void setting_and_deleting_instrument() {
        assertTrue(billingController.getDefaultInstrument(tenant).isEmpty());

        var instrumentRequest = request("/billing/v1/tenant/tenant1/instrument", PATCH)
                .data("{\"active\": \"id-1\"}")
                .roles(tenantRole);

        tester.assertResponse(instrumentRequest,"OK");
        assertEquals("id-1", billingController.getDefaultInstrument(tenant).get().getId());

        var deleteInstrumentRequest = request("/billing/v1/tenant/tenant1/instrument/id-1", DELETE)
                .roles(tenantRole);

        tester.assertResponse(deleteInstrumentRequest,"OK");
        assertTrue(billingController.getDefaultInstrument(tenant).isEmpty());
    }

    @Test
    public void response_list_bills() {
        var invoice = createInvoice();

        billingController.addInvoice(tenant, invoice, true);
        billingController.addInvoice(tenant, invoice, false);
        billingController.setPlan(tenant, PlanId.from("some-plan"), true);

        var request = request("/billing/v1/tenant/tenant1/billing?until=2020-05-28").roles(tenantRole);
        tester.assertResponse(request, new File("tenant-billing-view"));

    }

    @Test
    public void test_invoice_creation() {
        var invoices = billingController.getInvoicesForTenant(tenant);
        assertEquals(0, invoices.size());

        String requestBody = "{\"tenant\":\"tenant1\", \"startTime\":\"2020-04-20\", \"endTime\":\"2020-05-20\"}";
        var request = request("/billing/v1/invoice", POST)
                .data(requestBody)
                .roles(tenantRole);

        tester.assertResponse(request, accessDenied, 403);
        request.roles(financeAdmin);
        tester.assertResponse(request, new File("invoice-creation-response"));

        invoices = billingController.getInvoicesForTenant(tenant);
        assertEquals(1, invoices.size());
        Invoice invoice = invoices.get(0);
        assertEquals(invoice.getStartTime().toString(), "2020-04-20T00:00Z[UTC]");
        assertEquals(invoice.getEndTime().toString(), "2020-05-20T00:00Z[UTC]");
    }

    @Test
    public void adding_and_listing_line_item() {

        var requestBody = "{" +
                "\"description\":\"some description\"," +
                "\"amount\":\"123.45\" " +
                "}";

        var request = request("/billing/v1/invoice/tenant/tenant1/line-item", POST)
                .data(requestBody)
                .roles(financeAdmin);

        tester.assertResponse(request, "{\"message\":\"Added line item for tenant tenant1\"}");

        var lineItems = billingController.getUnusedLineItems(tenant);
        Assert.assertEquals(1, lineItems.size());
        Invoice.LineItem lineItem = lineItems.get(0);
        assertEquals("some description", lineItem.description());
        assertEquals(new BigDecimal("123.45"), lineItem.amount());

        request = request("/billing/v1/invoice/tenant/tenant1/line-item")
                .roles(financeAdmin);

        tester.assertResponse(request, new File("line-item-list"));
    }

    @Test
    public void adding_new_status() {
        billingController.addInvoice(tenant, createInvoice(), true);

        var requestBody = "{\"status\":\"DONE\"}";
        var request = request("/billing/v1/invoice/id-1/status", POST)
                .data(requestBody)
                .roles(financeAdmin);
        tester.assertResponse(request, "{\"message\":\"Updated status of invoice id-1\"}");

        var invoice = billingController.getInvoicesForTenant(tenant).get(0);
        assertEquals("DONE", invoice.status());
    }

    @Test
    public void list_all_uninvoiced_items() {
        tester.controller().tenants().create(new CloudTenantSpec(tenant, ""), new Auth0Credentials(() -> "foo", Set.of(Role.hostedOperator())));
        tester.controller().tenants().create(new CloudTenantSpec(tenant2, ""), new Auth0Credentials(() -> "foo", Set.of(Role.hostedOperator())));

        var invoice = createInvoice();
        billingController.setPlan(tenant, PlanId.from("some-plan"), true);
        billingController.setPlan(tenant2, PlanId.from("some-plan"), true);
        billingController.addInvoice(tenant, invoice, false);
        billingController.addLineItem(tenant, "support", new BigDecimal("42"), "Smith");
        billingController.addInvoice(tenant2, invoice, false);

        var request = request("/billing/v1/billing?until=2020-05-28").roles(financeAdmin);

        tester.assertResponse(request, new File("billing-all-tenants"));
    }

    @Test
    public void setting_plans() {
        var planRequest = request("/billing/v1/tenant/tenant1/plan", PATCH)
                .data("{\"plan\": \"new-plan\"}")
                .roles(tenantRole);
        tester.assertResponse(planRequest, "Plan: new-plan");
        assertEquals("new-plan", billingController.getPlan(tenant).value());
    }

    @Test
    public void csv_export() {
        var invoice = createInvoice();
        billingController.addInvoice(tenant, invoice, true);
        var csvRequest = request("/billing/v1/invoice/export", GET).roles(financeAdmin);
        tester.assertResponse(csvRequest.get(), new File("billing-all-invoices"), 200, false);
    }

    @Test
    public void patch_collection_method() {
        test_patch_collection_with_field_name("collectionMethod");
        test_patch_collection_with_field_name("collection");
    }

    private void test_patch_collection_with_field_name(String fieldName) {
        var planRequest = request("/billing/v1/tenant/tenant1/collection", PATCH)
                .data("{\"" + fieldName + "\": \"invoice\"}")
                .roles(financeAdmin);
        tester.assertResponse(planRequest, "Collection method updated to INVOICE");
        assertEquals(CollectionMethod.INVOICE, billingController.getCollectionMethod(tenant));

        // Test that not event tenant administrators can do this
        planRequest = request("/billing/v1/tenant/tenant1/collection", PATCH)
                .data("{\"collectionMethod\": \"epay\"}")
                .roles(tenantRole);
        tester.assertResponse(planRequest, accessDenied, 403);
        assertEquals(CollectionMethod.INVOICE, billingController.getCollectionMethod(tenant));
    }

    private Invoice createInvoice() {
        var start = LocalDate.of(2020, 5, 23).atStartOfDay(ZoneId.systemDefault());
        var end = start.plusDays(5);
        var statusHistory = new Invoice.StatusHistory(new TreeMap<>(Map.of(start, "OPEN")));
        return new Invoice(
                Invoice.Id.of("id-1"),
                TenantName.defaultName(),
                statusHistory,
                List.of(createLineItem(start)),
                start,
                end
        );
    }

    private Invoice.LineItem createLineItem(ZonedDateTime addedAt) {
        return new Invoice.LineItem(
                "some-id",
                "description",
                new BigDecimal("123.00"),
                "some-plan",
                "Smith",
                addedAt
        );
    }

}
