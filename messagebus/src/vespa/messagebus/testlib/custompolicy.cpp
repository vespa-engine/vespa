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
                           const std::vector<uint32_t> consumableErrors,
                           const std::vector<Route> &routes) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors(consumableErrors),
    _routes(routes)
{
}

CustomPolicy::~CustomPolicy() {
}

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
    for (std::vector<uint32_t>::iterator it = _consumableErrors.begin();
         it != _consumableErrors.end(); ++it)
    {
        context.addConsumableError(*it);
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


CustomPolicyFactory::CustomPolicyFactory() :
    _selectOnRetry(true),
    _consumableErrors()
{
}

CustomPolicyFactory::CustomPolicyFactory(bool selectOnRetry) :
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

CustomPolicyFactory::CustomPolicyFactory(bool selectOnRetry, const std::vector<uint32_t> consumableErrors) :
    _selectOnRetry(selectOnRetry),
    _consumableErrors(consumableErrors)
{
}

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

    std::vector<Route> routes;
    parseRoutes(param, routes);

    LOG(info, "Creating custom policy; selectOnRetry = %d, consumableErrors = %s, param = '%s'.",
        _selectOnRetry, str.c_str(), param.c_str());
    IRoutingPolicy::UP ret(new CustomPolicy(_selectOnRetry, _consumableErrors, routes));
    return ret;
}


void
CustomPolicyFactory::parseRoutes(const string &str,
                                 std::vector<Route> &routes)
{
    typedef boost::char_separator<char> Separator;
    typedef boost::tokenizer<Separator> Tokenizer;
    Separator separator(",");
    std::string stdstr(str);
    Tokenizer tokenizer(stdstr, separator);
    for (Tokenizer::iterator it = tokenizer.begin();
         it != tokenizer.end(); ++it)
    {
        routes.push_back(Route::parse(*it));
    }
}

} // namespace mbus
