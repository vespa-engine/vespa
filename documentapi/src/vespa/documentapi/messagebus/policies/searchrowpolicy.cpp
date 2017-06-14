// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchrowpolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchrowpolicy");

namespace documentapi {

SearchRowPolicy::SearchRowPolicy(const string &param) :
    _minOk(0)
{
    if (param.length() > 0) {
        int minOk = atoi(param.c_str());
        if (minOk > 0) {
            _minOk = (uint32_t)minOk;
        } else {
            LOG(warning,
                "Ignoring a request to set the minimum number of OK replies to %d because it makes no sense. "
                "This routing policy will not allow any recipient to be out of service.", minOk);
        }
    }
}

SearchRowPolicy::~SearchRowPolicy() {}

void
SearchRowPolicy::select(mbus::RoutingContext &context)
{
    std::vector<mbus::Route> recipients;
    context.getMatchedRecipients(recipients);
    context.addChildren(recipients);
    context.setSelectOnRetry(false);
    if (_minOk > 0) {
        context.addConsumableError(mbus::ErrorCode::SERVICE_OOS);
    }
}

void
SearchRowPolicy::merge(mbus::RoutingContext &context)
{
    if (_minOk > 0) {
        std::set<uint32_t> oosReplies;
        uint32_t idx = 0;
        for (mbus::RoutingNodeIterator it = context.getChildIterator();
             it.isValid(); it.next())
        {
            const mbus::Reply &ref = it.getReplyRef();
            if (ref.hasErrors() && DocumentProtocol::hasOnlyErrorsOfType(ref, mbus::ErrorCode::SERVICE_OOS)) {
                oosReplies.insert(idx);
            }
            ++idx;
        }
        if (context.getNumChildren() - oosReplies.size() >= _minOk) {
            DocumentProtocol::merge(context, oosReplies);
            return;
        }
    }
    DocumentProtocol::merge(context);
}

}
