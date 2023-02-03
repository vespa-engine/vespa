// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "custompolicy.h"
#include "simpleprotocol.h"
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/routing/routingcontext.h>
#include <boost/tokenizer.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".custompolicy");

namespace mbus {

CustomPolicy::CustomPolicy(bool selectOnRetry,
                           std::vector<uint32_t> consumableErrors,
                           std::vector<Route> routes) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors(std::move(consumableErrors)),
    _routes(std::move(routes))
{
}

CustomPolicy::~CustomPolicy() = default;

void
CustomPolicy::select(RoutingContext &context)
{
    string str = "Selecting { ";
    for (uint32_t i = 0; i < _routes.size(); ++i) {
        str.append("'");
        str.append(_routes[i].toString());
        str.append("'");
        if (i < _routes.size() - 1) {
            str.append(", ");
        }
    }
    str.append(" }.");
    context.trace(1, str);
    context.setSelectOnRetry(_selectOnRetry);
    for (unsigned int & _consumableError : _consumableErrors) {
        context.addConsumableError(_consumableError);
    }
    context.addChildren(_routes);
}

void
CustomPolicy::merge(RoutingContext &context)
{
    Reply::UP ret(new EmptyReply());
    std::vector<string> routes;
    for (RoutingNodeIterator it = context.getChildIterator();
         it.isValid(); it.next())
    {
        routes.push_back(it.getRoute().toString());
        const Reply &reply = it.getReplyRef();
        for (uint32_t i = 0; i < reply.getNumErrors(); ++i) {
            ret->addError(reply.getError(i));
        }
    }
    context.setReply(std::move(ret));
    string str = "Merged { ";
    for (uint32_t i = 0; i < routes.size(); ++i) {
        str.append("'");
        str.append(routes[i]);
        str.append("'");
        if (i < _routes.size() - 1) {
            str.append(", ");
        }
    }
    str.append(" }.");
    context.trace(1, str);
}

CustomPolicyFactory::CustomPolicyFactory(bool selectOnRetry) noexcept :
    _selectOnRetry(selectOnRetry),
    _consumableErrors()
{
}

CustomPolicyFactory::CustomPolicyFactory(bool selectOnRetry, uint32_t consumableError) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors()
{
    _consumableErrors.push_back(consumableError);
}

CustomPolicyFactory::CustomPolicyFactory(bool selectOnRetry, std::vector<uint32_t> consumableErrors) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors(std::move(consumableErrors))
{
}

CustomPolicyFactory::~CustomPolicyFactory() = default;

IRoutingPolicy::UP
CustomPolicyFactory::create(const string &param)
{
    string str = "{ ";
    for (uint32_t i = 0; i < _consumableErrors.size(); ++i) {
        str.append(ErrorCode::getName(_consumableErrors[i]));
        if (i < _consumableErrors.size() - 1) {
            str.append(", ");
        }
    }
    str.append(" }");

    LOG(info, "Creating custom policy; selectOnRetry = %d, consumableErrors = %s, param = '%s'.",
        _selectOnRetry, str.c_str(), param.c_str());
    return std::make_unique<CustomPolicy>(_selectOnRetry, _consumableErrors, parseRoutes(param));
}


std::vector<Route>
CustomPolicyFactory::parseRoutes(const string &str)
{
    std::vector<Route> routes;
    using Separator = boost::char_separator<char>;
    using Tokenizer = boost::tokenizer<Separator>;
    Separator separator(",");
    std::string stdstr(str);
    Tokenizer tokenizer(stdstr, separator);
    for (Tokenizer::iterator it = tokenizer.begin();
         it != tokenizer.end(); ++it)
    {
        routes.push_back(Route::parse(*it));
    }
    return routes;
}

} // namespace mbus
