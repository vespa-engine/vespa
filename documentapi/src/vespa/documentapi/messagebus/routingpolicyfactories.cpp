// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/config/subscription/configuri.h>

using namespace documentapi;

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::AndPolicyFactory::createPolicy(const string &param) const
{
    return std::make_unique<ANDPolicy>(param);
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::MessageTypePolicyFactory::createPolicy(const string &param) const
{
    return std::make_unique<MessageTypePolicy>(config::ConfigUri(param));
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::ContentPolicyFactory::createPolicy(const string &param) const
{
    auto ret = std::make_unique<ContentPolicy>(param);
    string error = static_cast<ContentPolicy&>(*ret).getError();
    if (!error.empty()) {
        return std::make_unique<ErrorPolicy>(error);
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
        return std::make_unique<ErrorPolicy>(error);
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
    auto ret = std::make_unique<DocumentRouteSelectorPolicy>(_repo, config::ConfigUri(param.empty() ? _configId : param));
    string error = static_cast<DocumentRouteSelectorPolicy&>(*ret).getError();
    if (!error.empty()) {
        return std::make_unique<ErrorPolicy>(error);
    }
    return ret;
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::ExternPolicyFactory::createPolicy(const string &param) const
{
    auto ret = std::make_unique<ExternPolicy>(param);
    string error = static_cast<ExternPolicy&>(*ret).getError();
    if (!error.empty()) {
        return std::make_unique<ErrorPolicy>(error);
    }
    return ret;
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::LocalServicePolicyFactory::createPolicy(const string &param) const
{
    return std::make_unique<LocalServicePolicy>(param);
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::RoundRobinPolicyFactory::createPolicy(const string &param) const
{
    return std::make_unique<RoundRobinPolicy>(param);
}

mbus::IRoutingPolicy::UP
RoutingPolicyFactories::SubsetServicePolicyFactory::createPolicy(const string &param) const
{
    return std::make_unique<SubsetServicePolicy>(param);
}
