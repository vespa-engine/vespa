// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "simpleprotocol.h"
#include <vespa/messagebus/routing/iroutingpolicy.h>
#include <vespa/messagebus/routing/route.h>

namespace mbus {

class CustomPolicy : public IRoutingPolicy {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _consumableErrors;
    std::vector<Route>    _routes;

public:
    CustomPolicy(bool selectOnRetry,
                 std::vector<uint32_t> consumableErrors,
                 std::vector<Route> routes);
    ~CustomPolicy() override;

    void select(RoutingContext &context) override;
    void merge(RoutingContext &context) override;
};

class CustomPolicyFactory : public SimpleProtocol::IPolicyFactory {
private:
    bool                  _selectOnRetry;
    std::vector<uint32_t> _consumableErrors;
public:
    CustomPolicyFactory() noexcept : CustomPolicyFactory(true) { }
    explicit CustomPolicyFactory(bool selectOnRetry) noexcept;
    CustomPolicyFactory(bool selectOnRetry, uint32_t consumableError);
    CustomPolicyFactory(bool selectOnRetry, std::vector<uint32_t> consumableErrors);
    ~CustomPolicyFactory() override;

    IRoutingPolicy::UP create(const string &param) override;
    [[nodiscard]] static std::vector<Route> parseRoutes(const string &str);
};

} // namespace mbus
