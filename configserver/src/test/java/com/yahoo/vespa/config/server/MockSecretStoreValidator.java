// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.config.provision.SystemName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.SecretStoreProvider;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.restapi.StringResponse;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.application.Application;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.vespa.config.server.http.SecretStoreValidator;

/**
 * @author olaa
 */
public class MockSecretStoreValidator extends SecretStoreValidator {

    public MockSecretStoreValidator() {
        super(new SecretStoreProvider().get());
    }

    public HttpResponse validateSecretStore(Application application, SystemName system, Slime slime) {
        return new SlimeJsonResponse(slime);
    }
}
