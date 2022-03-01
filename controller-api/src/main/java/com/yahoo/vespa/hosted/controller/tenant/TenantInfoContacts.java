// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import java.util.EnumMap;

/**
 * @author ogronnesby
 */
public class TenantInfoContacts {
    private final EnumMap<ContactType, TenantContact> contacts;

    TenantInfoContacts() {
        this.contacts = new EnumMap<>(ContactType.class);
    }

    public enum ContactType {
        DEFAULT("default"),
        PRODUCT("product");

        private final String value;

        ContactType(String value) {
            this.value = value;
        }

        public String value() { return value; }
    }
}
