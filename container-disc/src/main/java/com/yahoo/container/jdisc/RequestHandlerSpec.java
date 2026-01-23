// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc;

import java.util.Objects;

/**
 * A specification provided by a request handler.
 * Available through request context attribute
 *
 * @author mortent
 */
public class RequestHandlerSpec {

    public static final String ATTRIBUTE_NAME = RequestHandlerSpec.class.getName();
    public static final RequestHandlerSpec DEFAULT_INSTANCE = RequestHandlerSpec.builder().build();

    private final AclMapping aclMapping;

    private RequestHandlerSpec(AclMapping aclMapping) {
        this.aclMapping = aclMapping;
    }

    public AclMapping aclMapping() {
        return aclMapping;
    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder {

        private AclMapping aclMapping = HttpMethodAclMapping.standard().build();

        public Builder withAclMapping(AclMapping aclMapping) {
            this.aclMapping = Objects.requireNonNull(aclMapping);
            return this;
        }

        public RequestHandlerSpec build() {
            return new RequestHandlerSpec(aclMapping);
        }
    }
}

