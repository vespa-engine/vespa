package com.yahoo.application.container.filters;

import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;

import java.security.Principal;

/**
 * @author bratseth
 */
public class ThrowingFilter implements SecurityRequestFilter {

    @Override
    public void filter(DiscFilterRequest discFilterRequest, ResponseHandler responseHandler) {
        if (1==1) throw new RuntimeException("Filter ran");
        discFilterRequest.setUserPrincipal(new MockPrincipal());
    }

    private static class MockPrincipal implements Principal {

        @Override
        public String getName() {
            return "testuser";
        }

    }

}
