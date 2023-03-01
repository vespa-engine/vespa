// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.ca.restapi.mock;

import com.yahoo.vespa.hosted.athenz.instanceproviderservice.InstanceConfirmation;
import com.yahoo.vespa.hosted.athenz.instanceproviderservice.InstanceValidator;

/**
 * @author mortent
 */
public class InstanceValidatorMock extends InstanceValidator {

    public InstanceValidatorMock() {
        super(null, null, null, null, null);
    }

    @Override
    public boolean isValidInstance(InstanceConfirmation instanceConfirmation) {
        return instanceConfirmation.attributes.get(SAN_DNS_ATTRNAME) != null &&
               instanceConfirmation.attributes.get(SAN_IPS_ATTRNAME) != null;
    }

    @Override
    public boolean isValidRefresh(InstanceConfirmation confirmation) {
        return confirmation.attributes.get(SAN_DNS_ATTRNAME) != null &&
               confirmation.attributes.get(SAN_IPS_ATTRNAME) != null;
    }
}
