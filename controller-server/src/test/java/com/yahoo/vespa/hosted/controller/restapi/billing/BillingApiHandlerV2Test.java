// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Bill;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillStatus;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.StatusHistory;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * @author ogronnesby
 */
public class BillingApiHandlerV2Test extends ControllerContainerCloudTest {

    private static final String responseFiles = "src/test/java/com/yahoo/vespa/hosted/controller/restapi/billing/responses/";

    private static final TenantName tenant = TenantName.from("tenant1");
    private static final TenantName tenant2 = TenantName.from("tenant2");
    private static final Set<Role> tenantReader = Set.of(Role.reader(tenant));
    private static final Set<Role> tenantAdmin = Set.of(Role.administrator(tenant));
    private static final Set<Role> financeAdmin = Set.of(Role.hostedAccountant());

    private MockBillingController billingController;
    private ContainerTester tester;

    @BeforeEach
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        tester.controller().tenants().create(new CloudTenantSpec(tenant, ""), new Auth0Credentials(() -> "foo", Set.of(Role.hostedOperator())));
        var clock = (ManualClock) tester.controller().serviceRegistry().clock();
        clock.setInstant(Instant.parse("2021-04-13T00:00:00Z"));
        billingController = (MockBillingController) tester.serviceRegistry().billingController();
        billingController.addBill(tenant, createBill(), true);
    }

    @Override
    protected String variablePartXml() {
        return "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControlRequests'/>\n" +
                "  <component id='com.yahoo.vespa.hosted.controller.security.CloudAccessControl'/>\n" +

                "  <handler id='com.yahoo.vespa.hosted.controller.restapi.billing.BillingApiHandlerV2'>\n" +
                "    <binding>http://*/billing/v2/*</binding>\n" +
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
    void require_tenant_info() {
        var request = request("/billing/v2/tenant/" + tenant.value()).roles(tenantReader);
        tester.assertResponse(request, "{\"tenant\":\"tenant1\",\"plan\":{\"id\":\"trial\",\"name\":\"Free Trial - for testing purposes\"},\"collection\":\"AUTO\"}");
    }

    @Test
    void require_accountant_for_update_collection() {
        var request = request("/billing/v2/tenant/" + tenant.value(), Request.Method.PATCH)
                .data("{\"collection\": \"INVOICE\"}");

        var forbidden = request.roles(tenantAdmin);
        tester.assertResponse(forbidden, """
                {
                  "code" : 403,
                  "message" : "Access denied"
                }""", 403);

        var success = request.roles(financeAdmin);
        tester.assertResponse(success, """
                {"tenant":"tenant1","plan":{"id":"trial","name":"Free Trial - for testing purposes"},"collection":"INVOICE"}""");
    }

    @Test
    void require_tenant_usage() {
        var request = request("/billing/v2/tenant/" + tenant + "/usage").roles(tenantReader);
        tester.assertResponse(request, "{\"from\":\"2021-04-13\",\"to\":\"2021-04-13\",\"total\":\"0.00\",\"items\":[]}");
    }

    @Test
    void require_tenant_invoice() {
        var listRequest = request("/billing/v2/tenant/" + tenant + "/bill").roles(tenantReader);
        tester.assertResponse(listRequest, "{\"invoices\":[{\"id\":\"id-1\",\"from\":\"2020-05-23\",\"to\":\"2020-05-28\",\"total\":\"123.00\",\"status\":\"OPEN\"}]}");

        var singleRequest = request("/billing/v2/tenant/" + tenant + "/bill/id-1").roles(tenantReader);
        tester.assertResponse(singleRequest, """
                {"id":"id-1","from":"2020-05-23","to":"2020-05-28","total":"123.00","status":"OPEN","statusHistory":[{"at":"2020-05-23T00:00:00Z","status":"OPEN"}],"items":[{"id":"some-id","description":"description","amount":"123.00","plan":{"id":"paid","name":"Paid Plan - for testing purposes"},"majorVersion":0,"cpu":{},"memory":{},"disk":{},"gpu":{}}]}""");
    }

    @Test
    void require_accountant_summary() {
        var tenantRequest = request("/billing/v2/accountant").roles(tenantReader);
        tester.assertResponse(tenantRequest, "{\n" +
                "  \"code\" : 403,\n" +
                "  \"message\" : \"Access denied\"\n" +
                "}", 403);

        var accountantRequest = request("/billing/v2/accountant").roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, """
                {"tenants":[{"tenant":"tenant1","plan":{"id":"trial","name":"Free Trial - for testing purposes"},"quota":{"budget":-1.0},"collection":"AUTO","lastBill":"1970-01-01","unbilled":"0.00"}]}""");
    }

    @Test
    void require_accountant_preview() {
        var accountantRequest = request("/billing/v2/accountant/preview").roles(Role.hostedAccountant());
        billingController.uncommittedBills.put(tenant, createBill());

        tester.assertResponse(accountantRequest, """
                        {"tenants":[{"tenant":"tenant1","plan":{"id":"trial","name":"Free Trial - for testing purposes"},"quota":{"budget":-1.0},"collection":"AUTO","lastBill":"2020-05-23","unbilled":"123.00"}]}""");
    }

    @Test
    void require_accountant_tenant_preview() {
        var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/preview").roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, "{\"id\":\"empty\",\"from\":\"2021-04-13\",\"to\":\"2021-04-12\",\"total\":\"0.00\",\"status\":\"OPEN\",\"statusHistory\":[{\"at\":\"2021-04-13T00:00:00Z\",\"status\":\"OPEN\"}],\"items\":[]}");
    }

    @Test
    void require_accountant_tenant_bill() {
        var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/preview", Request.Method.POST)
                .roles(Role.hostedAccountant())
                .data("{\"from\": \"2020-05-01\",\"to\": \"2020-06-01\"}");
        tester.assertResponse(accountantRequest, "{\"message\":\"Created bill id-123\"}");
    }

    @Test
    void require_list_of_all_plans() {
        var accountantRequest = request("/billing/v2/accountant/plans")
                .roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, "{\"plans\":[{\"id\":\"trial\",\"name\":\"Free Trial - for testing purposes\"},{\"id\":\"paid\",\"name\":\"Paid Plan - for testing purposes\"},{\"id\":\"none\",\"name\":\"None Plan - for testing purposes\"}]}");
    }

   @Test
    void require_additional_items_empty() {
        var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/items")
                .roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, """
                {"items":[]}""");
   }

   @Test
    void require_additional_items_with_content() {
       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/items", Request.Method.POST)
                   .roles(Role.hostedAccountant())
                   .data("""
                        {
                            "description": "Additional support costs",
                            "amount": "123.45"
                        }""");
           tester.assertResponse(accountantRequest, """
                {"message":"Added line item for tenant tenant1"}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/items")
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"items":[{"id":"line-item-id","description":"Additional support costs","amount":"123.45","plan":{"id":"paid","name":"Paid Plan - for testing purposes"},"majorVersion":0,"cpu":{},"memory":{},"disk":{},"gpu":{}}]}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/item/line-item-id", Request.Method.DELETE)
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"message":"Successfully deleted line item line-item-id"}""");
       }
   }

   @Test
    void require_current_plan() {
       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/plan")
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"id":"trial","name":"Free Trial - for testing purposes"}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/plan", Request.Method.POST)
                   .roles(Role.hostedAccountant())
                   .data("""
                           {"id": "paid"}""");
           tester.assertResponse(accountantRequest, """
                   {"message":"Plan: paid"}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/plan")
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"id":"paid","name":"Paid Plan - for testing purposes"}""");
       }
   }

   @Test
    void require_current_collection() {
       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/collection")
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"collection":"AUTO"}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/collection", Request.Method.POST)
                   .roles(Role.hostedAccountant())
                   .data("""
                           {"collection": "INVOICE"}""");
           tester.assertResponse(accountantRequest, """
                   {"message":"Collection: INVOICE"}""");
       }

       {
           var accountantRequest = request("/billing/v2/accountant/tenant/tenant1/collection")
                   .roles(Role.hostedAccountant());
           tester.assertResponse(accountantRequest, """
                   {"collection":"INVOICE"}""");
       }
   }

   @Test
    void require_accountant_tenant() {
        var accountantRequest = request("/billing/v2/accountant/tenant/tenant1")
                .roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, """
                {"tenant":"tenant1","plan":{"id":"trial","name":"Free Trial - for testing purposes","billed":false,"supported":false},"billing":{},"collection":"AUTO"}""");
   }

    @Test
    void lists_accepted_countries() {
        var req = request("/billing/v2/countries").roles(tenantReader);
        tester.assertJsonResponse(req, new File("accepted-countries.json"));
    }

    @Test
    void summarize_bill() {
        var req = request("/billing/v2/accountant/bill/id-1/summary?keys=plan,architecture")
                .roles(Role.hostedAccountant());
        tester.assertResponse(req, """
                {"id":"id-1","summary":[{"key":{"plan":"paid","architecture":null},"summary":{"cpu":{"cost":"0","hours":"0"},"memory":{"cost":"0","hours":"0"},"disk":{"cost":"0","hours":"0"},"gpu":{"cost":"0","hours":"0"}}}],"additional":[]}""");
    }

    private static Bill createBill() {
        var start = LocalDate.of(2020, 5, 23).atStartOfDay(ZoneOffset.UTC);
        var end = start.toLocalDate().plusDays(6).atStartOfDay(ZoneOffset.UTC);
        var statusHistory = new StatusHistory(new TreeMap<>(Map.of(start, BillStatus.OPEN)));
        return new Bill(
                Bill.Id.of("id-1"),
                TenantName.defaultName(),
                statusHistory,
                List.of(createLineItem(start)),
                start,
                end
        );
    }

    static Bill.LineItem createLineItem(ZonedDateTime addedAt) {
        return new Bill.LineItem(
                "some-id",
                "description",
                new BigDecimal("123.00"),
                "paid",
                "Smith",
                addedAt
        );
    }
}
