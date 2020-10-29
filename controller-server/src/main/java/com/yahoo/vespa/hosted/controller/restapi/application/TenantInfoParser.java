package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.slime.Inspector;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoAddress;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfoBillingContact;

public class TenantInfoParser {

    TenantInfo tenantInfoFromSlime(Inspector infoObject) {
        if (!infoObject.valid()) return TenantInfo.EmptyInfo;

        return TenantInfo.EmptyInfo
                .withName(infoObject.field("name").asString())
                .withEmail(infoObject.field("email").asString())
                .withWebsite(infoObject.field("website").asString())
                .withContactName(infoObject.field("contactName").asString())
                .withContactEmail(infoObject.field("contactEmail").asString())
                .withInvoiceEmail(infoObject.field("invoiceEmail").asString())
                .withAddress(tenantInfoAddressFromSlime(infoObject.field("address")))
                .withBillingContact(tenantInfoBillingContactFromSlime(infoObject.field("billingContact")));
    }

    private TenantInfoAddress tenantInfoAddressFromSlime(Inspector addressObject) {
        return TenantInfoAddress.EmptyAddress
                .withAddressLines(addressObject.field("addressLines").asString())
                .withPostalCodeOrZip(addressObject.field("postalCodeOrZip").asString())
                .withCity(addressObject.field("city").asString())
                .withStateRegionProvince(addressObject.field("stateRegionProvince").asString())
                .withCountry(addressObject.field("country").asString());
    }

    private TenantInfoBillingContact tenantInfoBillingContactFromSlime(Inspector billingObject) {
        return TenantInfoBillingContact.EmptyBillingContact
                .withName(billingObject.field("name").asString())
                .withEmail(billingObject.field("email").asString())
                .withPhone(billingObject.field("phone").asString())
                .withAddress(tenantInfoAddressFromSlime(billingObject.field("address")));
    }
}
