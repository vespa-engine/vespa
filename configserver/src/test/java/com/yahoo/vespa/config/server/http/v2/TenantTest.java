// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.http.SessionResponse;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.After;
import org.junit.Before;

/**
 * Supertype for tests in the multi tenant application API
 * 
 * @author vegardh
 *
 */
public class TenantTest extends TestWithCurator {

    protected TenantRepository tenantRepository;

    @Before
    public void setupTenants() throws Exception {
        tenantRepository = createTenants();
    }

    @After
    public void closeTenants() throws IOException {
        tenantRepository.close();
    }

    protected TenantRepository createTenants() throws Exception {
        return new TenantRepository(new TestComponentRegistry.Builder().curator(curator).build());
    }

    protected Executor testExecutor() {
        return new Executor() {            
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }
    
    protected void assertResponseEquals(SessionResponse response, String payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        assertEquals(baos.toString("UTF-8"), payload);
    }

}
