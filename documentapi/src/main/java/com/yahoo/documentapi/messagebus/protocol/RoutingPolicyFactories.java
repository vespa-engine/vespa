// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;

/**
 * @author Simon Thoresen Hult
 * @author jonmv
 */
class RoutingPolicyFactories {

    private RoutingPolicyFactories() { }

    static class AndPolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new ANDPolicy(param);
        }
    }

    static class ContentPolicyFactory implements RoutingPolicyFactory {
        private final DistributionConfig distributionConfig;
        public ContentPolicyFactory(DistributionConfig config) { this.distributionConfig = config; }
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new ContentPolicy(param, distributionConfig);
        }
    }

    static class MessageTypePolicyFactory implements RoutingPolicyFactory {

        private final String configId;
        private final DocumentProtocolPoliciesConfig config;

        public MessageTypePolicyFactory(String configId, DocumentProtocolPoliciesConfig config) {
            this.configId = configId;
            this.config = config;
        }

        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            if (config != null) {
                if (config.cluster(param) == null)
                    return new ErrorPolicy("No message type config for cluster '" + param + "'");

                return new MessageTypePolicy(config.cluster(param));
            }
            return new MessageTypePolicy(param == null || param.isEmpty() ? configId : param);
        }
    }

    static class DocumentRouteSelectorPolicyFactory implements RoutingPolicyFactory {

        private final String configId;
        private final DocumentProtocolPoliciesConfig config;

        public DocumentRouteSelectorPolicyFactory(String configId, DocumentProtocolPoliciesConfig config) {
            this.configId = configId;
            this.config = config;
        }

        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            if (config != null) {
                try {
                    return new DocumentRouteSelectorPolicy(config);
                }
                catch (IllegalArgumentException e) {
                    return new ErrorPolicy(e.getMessage());
                }
            }
            DocumentRouteSelectorPolicy ret = new DocumentRouteSelectorPolicy(param == null || param.isEmpty() ?
                                                                              configId : param);
            String error = ret.getError();
            if (error != null) {
                return new ErrorPolicy(error);
            }
            return ret;
        }
    }

    static class ExternPolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            ExternPolicy ret = new ExternPolicy(param);
            String error = ret.getError();
            if (error != null) {
                return new ErrorPolicy(error);
            }
            return ret;
        }
    }

    static class LocalServicePolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new LocalServicePolicy(param);
        }
    }

    static class RoundRobinPolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new RoundRobinPolicy();
        }
    }

    static class LoadBalancerPolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new LoadBalancerPolicy(param);
        }
    }

    static class SubsetServicePolicyFactory implements RoutingPolicyFactory {
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new SubsetServicePolicy(param);
        }
    }

}
