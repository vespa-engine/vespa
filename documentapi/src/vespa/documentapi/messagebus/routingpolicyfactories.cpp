// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingpolicyfactories.h"
#include <vespa/documentapi/messagebus/policies/andpolicy.h>
#include <vespa/documentapi/messagebus/policies/contentpolicy.h>
#include <vespa/documentapi/messagebus/policies/documentrouteselectorpolicy.h>
#include <vespa/documentapi/messagebus/policies/errorpolicy.h>
#include <vespa/documentapi/messagebus/policies/externpolicy.h>
#include <vespa/documentapi/messagebus/policies/loadbalancerpolicy.h>
#include <vespa/documentapi/messagebus/policies/localservicepolicy.h>
#include <vespa/documentapi/messagebus/policies/messagetypepolicy.h>
#include <vespa/documentapi/messagebus/policies/roundrobinpolicy.h>
#include <vespa/documentapi/messagebus/policies/subsetservicepolicy.h>

using namespace documentapi;

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::AndPolicyFactory::createPolicy(const string &param) const
{
    return mbus::IRoutingPolicy::UP(new ANDPolicy(param));
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::MessageTypePolicyFactory::createPolicy(const string &param) const
{
    return mbus::IRoutingPolicy::UP(new MessageTypePolicy(param));
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::ContentPolicyFactory::createPolicy(const string &param) const
{
    mbus::IRoutingPolicy::UP ret(new ContentPolicy(param));
    string error = static_cast<ContentPolicy&>(*ret).getError();
    if (!error.empty()) {
        ret.reset(new ErrorPolicy(error));
    }
    return ret;
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::LoadBalancerPolicyFactory::createPolicy(const string &param) const
{
    mbus::IRoutingPolicy::UP ret(new LoadBalancerPolicy(param));
    string error = static_cast<LoadBalancerPolicy&>(*ret).getError();
    if (!error.empty()) {
        fprintf(stderr, "Got error %s\n", error.c_str());
        ret.reset(new ErrorPolicy(error));
    }
    return ret;
}

RoutingPolicyFactories::DocumentRouteSelectorPolicyFactory::
DocumentRouteSelectorPolicyFactory(const document::DocumentTypeRepo &repo,
                                   const string &configId) :
    _repo(repo),
    _configId(configId)
{
    // empty
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::DocumentRouteSelectorPolicyFactory::createPolicy(const string &param) const
{
    mbus::IRoutingPolicy::UP ret(new DocumentRouteSelectorPolicy(
                    _repo, param.empty() ? _configId : param));
    string error = static_cast<DocumentRouteSelectorPolicy&>(*ret).getError();
    if (!error.empty()) {
        ret.reset(new ErrorPolicy(error));
    }
    return ret;
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::ExternPolicyFactory::createPolicy(const string &param) const
{
    mbus::IRoutingPolicy::UP ret(new ExternPolicy(param));
    string error = static_cast<ExternPolicy&>(*ret).getError();
    if (!error.empty()) {
        ret.reset(new ErrorPolicy(error));
    }
    return ret;
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::LocalServicePolicyFactory::createPolicy(const string &param) const
{
    return mbus::IRoutingPolicy::UP(new LocalServicePolicy(param));
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::RoundRobinPolicyFactory::createPolicy(const string &param) const
{
    return mbus::IRoutingPolicy::UP(new RoundRobinPolicy(param));
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::SubsetServicePolicyFactory::createPolicy(const string &param) const
{
    return mbus::IRoutingPolicy::UP(new SubsetServicePolicy(param));
}
