// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/systemstate/systemstate.h>
#include "iroutingpolicyfactory.h"

namespace documentapi {

class RoutingPolicyFactories {
private:
    RoutingPolicyFactories() { /* abstract */ }

public:
    class AndPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class StoragePolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class MessageTypePolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class ContentPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class LoadBalancerPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class DocumentRouteSelectorPolicyFactory : public IRoutingPolicyFactory {
    private:
        const document::DocumentTypeRepo &_repo;
        string _configId;
    public:
        DocumentRouteSelectorPolicyFactory(
                const document::DocumentTypeRepo &repo,
                const string &configId);
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class ExternPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class LocalServicePolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class RoundRobinPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class SearchColumnPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class SearchRowPolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
    class SubsetServicePolicyFactory : public IRoutingPolicyFactory {
    public:
        mbus::IRoutingPolicy::UP createPolicy(const string &param) const;
    };
};

}

