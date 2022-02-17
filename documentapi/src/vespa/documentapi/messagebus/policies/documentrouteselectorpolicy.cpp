// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentrouteselectorpolicy.h"
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/select/parser.h>
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/getdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/documentignoredreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/routing/routingtable.h>
#include <vespa/messagebus/messagebus.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/config/subscription/configuri.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>

#include <vespa/log/log.h>
LOG_SETUP(".documentrouteselectorpolicy");

using document::select::Result;

namespace documentapi {

DocumentRouteSelectorPolicy::DocumentRouteSelectorPolicy(
        const document::DocumentTypeRepo &repo, const config::ConfigUri & configUri) :
    mbus::IRoutingPolicy(),
    config::IFetcherCallback<messagebus::protocol::DocumentrouteselectorpolicyConfig>(),
    _repo(repo),
    _lock(),
    _config(),
    _error("Not configured."),
    _fetcher(std::make_unique<config::ConfigFetcher>(configUri.getContext()))
{
    _fetcher->subscribe<messagebus::protocol::DocumentrouteselectorpolicyConfig>(configUri.getConfigId(), this);
    _fetcher->start();
}

DocumentRouteSelectorPolicy::~DocumentRouteSelectorPolicy() = default;

void
DocumentRouteSelectorPolicy::configure(std::unique_ptr<messagebus::protocol::DocumentrouteselectorpolicyConfig> cfg)
{
    string error = "";
    ConfigMap config;
    for (uint32_t i = 0; i < cfg->route.size(); i++) {
        const messagebus::protocol::DocumentrouteselectorpolicyConfig::Route &route = cfg->route[i];
        if (route.selector.empty()) {
            continue;
        }
        SelectorPtr selector;
        try {
            document::BucketIdFactory factory;
            document::select::Parser parser(_repo, factory);
            selector.reset(parser.parse(route.selector).release());
        }
        catch (document::select::ParsingFailedException &e) {
            error = vespalib::make_string("Error parsing selector '%s' for route '%s'; %s",
                                          route.selector.c_str(), route.name.c_str(),
                                          e.getMessage().c_str());
            break;
        }
        config[string(route.name)] = selector;
    }
    std::lock_guard guard(_lock);
    _config.swap(config);
    _error.swap(error);
}

const string &
DocumentRouteSelectorPolicy::getError() const
{
    std::lock_guard guard(_lock);
    return _error;
}

void
DocumentRouteSelectorPolicy::select(mbus::RoutingContext &context)
{
    // Require that recipients have been configured.
    if (!context.hasRecipients()) {
        context.setError(DocumentProtocol::ERROR_POLICY_FAILURE,
                         "No recipients configured.");
        return;
    }

    // Invoke private select method for each candidate recipient.
    {
        std::lock_guard guard(_lock);
        if (!_error.empty()) {
            context.setError(DocumentProtocol::ERROR_POLICY_FAILURE, _error);
            return;
        }
        for (uint32_t i = 0; i < context.getNumRecipients(); ++i) {
            const mbus::Route &recipient = context.getRecipient(i);
            vespalib::string routeName = recipient.toString();
            if (select(context, routeName)) {
                const mbus::Route *route = context.getMessageBus().getRoutingTable(DocumentProtocol::NAME)->getRoute(routeName);
                context.addChild(route != NULL ? *route : recipient);
            }
        }
    }
    context.setSelectOnRetry(false);

    // Notify that no children were selected, this is to differentiate this from the NO_RECIPIENTS_FOR_ROUTE error
    // that message bus will generate if there are no recipients and no reply.
    if (!context.hasChildren()) {
        context.setReply(mbus::Reply::UP(new DocumentIgnoredReply()));
    }
}

bool
DocumentRouteSelectorPolicy::select(mbus::RoutingContext &context, const vespalib::string &routeName)
{
    if (_config.empty()) {
        LOG(debug, "No config at all, select '%s'.", routeName.c_str());
        return true;
    }
    ConfigMap::const_iterator it = _config.find(routeName);
    if (it == _config.end()) {
        LOG(debug, "No config entry for route '%s', select it.", routeName.c_str());
        return true;
    }
    LOG_ASSERT(it->second.get() != NULL);

    // Select based on message content.
    const mbus::Message &msg = context.getMessage();
    switch(msg.getType()) {
    case DocumentProtocol::MESSAGE_PUTDOCUMENT:
        return it->second->contains(static_cast<const PutDocumentMessage&>(msg).getDocument()) == Result::True;

    case DocumentProtocol::MESSAGE_UPDATEDOCUMENT:
        return it->second->contains(static_cast<const UpdateDocumentMessage&>(msg).getDocumentUpdate()) != Result::False;

    case DocumentProtocol::MESSAGE_REMOVEDOCUMENT: {
        const RemoveDocumentMessage &removeMsg = static_cast<const RemoveDocumentMessage &>(msg);
        if (removeMsg.getDocumentId().hasDocType()) {
            return it->second->contains(removeMsg.getDocumentId()) != Result::False;
        } else {
            return true;
        }
    }
    case DocumentProtocol::MESSAGE_GETDOCUMENT: {
        const GetDocumentMessage &getMsg = static_cast<const GetDocumentMessage &>(msg);
        if (getMsg.getDocumentId().hasDocType()) {
            return it->second->contains(getMsg.getDocumentId()) != Result::False;
        } else {
            return true;
        }
    }
    default:
        return true;
    }
}

void
DocumentRouteSelectorPolicy::merge(mbus::RoutingContext &context)
{
    DocumentProtocol::merge(context);
}

}
