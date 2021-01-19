// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

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
        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new ContentPolicy(param);
        }
    }

    static class MessageTypePolicyFactory implements RoutingPolicyFactory {

        private final String configId;

        public MessageTypePolicyFactory(String configId) {
            this.configId = configId;
        }

        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            return new MessageTypePolicy((param == null || param.isEmpty()) ? configId : param);
        }

        public void destroy() {
        }
    }

    static class DocumentRouteSelectorPolicyFactory implements RoutingPolicyFactory {

        private final String configId;

        public DocumentRouteSelectorPolicyFactory(String configId) {
            this.configId = configId;
        }

        public DocumentProtocolRoutingPolicy createPolicy(String param) {
            DocumentRouteSelectorPolicy ret = new DocumentRouteSelectorPolicy((param == null || param.isEmpty()) ?
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
