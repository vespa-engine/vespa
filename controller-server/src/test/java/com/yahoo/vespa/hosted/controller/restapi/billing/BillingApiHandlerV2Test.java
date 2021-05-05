package com.yahoo.vespa.hosted.controller.restapi.billing;

import com.yahoo.application.container.handler.Request;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.role.Role;
import com.yahoo.vespa.hosted.controller.restapi.ContainerTester;
import com.yahoo.vespa.hosted.controller.restapi.ControllerContainerCloudTest;
import com.yahoo.vespa.hosted.controller.security.Auth0Credentials;
import com.yahoo.vespa.hosted.controller.security.CloudTenantSpec;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Set;

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

    private static final String ACCESS_DENIED = "{\n" +
            "  \"code\" : 403,\n" +
            "  \"message\" : \"Access denied\"\n" +
            "}";

    private MockBillingController billingController;
    private ContainerTester tester;

    @Before
    public void before() {
        tester = new ContainerTester(container, responseFiles);
        tester.controller().tenants().create(new CloudTenantSpec(tenant, ""), new Auth0Credentials(() -> "foo", Set.of(Role.hostedOperator())));
        var clock = (ManualClock) tester.controller().serviceRegistry().clock();
        clock.setInstant(Instant.parse("2021-04-13T00:00:00Z"));
        billingController = (MockBillingController) tester.serviceRegistry().billingController();
        billingController.addInvoice(tenant, BillingApiHandlerTest.createInvoice(), true);
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
    public void require_tenant_info() {
        var request = request("/billing/v2/tenant/" + tenant.value()).roles(tenantReader);
        tester.assertResponse(request, "{\"tenant\":\"tenant1\",\"plan\":\"trial\",\"collection\":\"AUTO\"}");
    }

    @Test
    public void require_admin_for_update_plan() {
        var request = request("/billing/v2/tenant/" + tenant.value(), Request.Method.PATCH)
                .data("{\"plan\": \"pay-as-you-go\"}");

        var forbidden = request.roles(tenantReader);
        tester.assertResponse(forbidden, ACCESS_DENIED, 403);
        var success = request.roles(tenantAdmin);
        tester.assertResponse(success, "{\"tenant\":\"tenant1\",\"plan\":\"pay-as-you-go\",\"collection\":\"AUTO\"}");
    }

    @Test
    public void require_accountant_for_update_collection() {
        var request = request("/billing/v2/tenant/" + tenant.value(), Request.Method.PATCH)
                .data("{\"collection\": \"INVOICE\"}");

        var forbidden = request.roles(tenantAdmin);
        tester.assertResponse(forbidden, "{\"error-code\":\"FORBIDDEN\",\"message\":\"Only accountant can change billing method\"}", 403);

        var success = request.roles(financeAdmin);
        tester.assertResponse(success, "{\"tenant\":\"tenant1\",\"plan\":\"trial\",\"collection\":\"INVOICE\"}");
    }

    @Test
    public void require_tenant_usage() {
        var request = request("/billing/v2/tenant/" + tenant + "/usage").roles(tenantReader);
        tester.assertResponse(request, "{\"from\":\"2021-04-13\",\"to\":\"2021-04-13\",\"total\":\"0.00\",\"items\":[]}");
    }

    @Test
    public void require_tenant_invoice() {
        var listRequest = request("/billing/v2/tenant/" + tenant + "/bill").roles(tenantReader);
        tester.assertResponse(listRequest, "{\"invoices\":[{\"id\":\"id-1\",\"from\":\"2020-05-23\",\"to\":\"2020-05-23\",\"total\":\"123.00\",\"status\":\"OPEN\"}]}");

        var singleRequest = request("/billing/v2/tenant/" + tenant + "/bill/id-1").roles(tenantReader);
        tester.assertResponse(singleRequest, "{\"id\":\"id-1\",\"from\":\"2020-05-23\",\"to\":\"2020-05-23\",\"total\":\"123.00\",\"status\":\"OPEN\",\"statusHistory\":[{\"at\":\"2020-05-23T00:00:00Z\",\"status\":\"OPEN\"}],\"items\":[{\"id\":\"some-id\",\"description\":\"description\",\"amount\":\"123.00\",\"plan\":\"some-plan\",\"planName\":\"Plan with id: some-plan\",\"cpu\":{},\"memory\":{},\"disk\":{}}]}");
    }

    @Test
    public void require_accountant_summary() {
        var tenantRequest = request("/billing/v2/accountant").roles(tenantReader);
        tester.assertResponse(tenantRequest, "{\n" +
                "  \"code\" : 403,\n" +
                "  \"message\" : \"Access denied\"\n" +
                "}", 403);

        var accountantRequest = request("/billing/v2/accountant").roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, "{\"tenants\":[{\"tenant\":\"tenant1\",\"plan\":\"trial\",\"collection\":\"AUTO\",\"lastBill\":null,\"unbilled\":\"0.00\"}]}");
    }

    @Test
    public void require_accountant_tenant_preview() {
        var accountantRequest = request("/billing/v2/accountant/preview/tenant/tenant1").roles(Role.hostedAccountant());
        tester.assertResponse(accountantRequest, "{\"id\":\"empty\",\"from\":\"2021-04-13\",\"to\":\"2021-04-13\",\"total\":\"0.00\",\"status\":\"OPEN\",\"statusHistory\":[{\"at\":\"2021-04-13T00:00:00Z\",\"status\":\"OPEN\"}],\"items\":[]}");
    }

    @Test
    public void require_accountant_tenant_bill() {
        var accountantRequest = request("/billing/v2/accountant/preview/tenant/tenant1", Request.Method.POST)
                .roles(Role.hostedAccountant())
                .data("{\"from\": \"2020-05-01\",\"to\": \"2020-06-01\"}");
        tester.assertResponse(accountantRequest, "{\"message\":\"Created bill id-123\"}");
    }
}
