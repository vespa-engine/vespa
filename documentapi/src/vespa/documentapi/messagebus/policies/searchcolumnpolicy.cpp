// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchcolumnpolicy.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/documentapi/messagebus/messages/getdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/batchdocumentupdatemessage.h>
#include <vespa/documentapi/messagebus/messages/multioperationmessage.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/util/hashmap.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchcolumnpolicy");

namespace documentapi {

SearchColumnPolicy::SearchColumnPolicy(const string &param) :
    _lock(),
    _factory(),
    _distributions(),
    _maxOOS(0)
{
    if (param.length() > 0) {
        int maxOOS = atoi(param.c_str());
        if (maxOOS >= 0) {
            _maxOOS = (uint32_t)maxOOS;
        } else {
            LOG(warning,
                "Ignoring a request to set the maximum number of OOS replies to %d because it makes no "
                "sense. This routing policy will not allow any recipient to be out of service.", maxOOS);
        }
    }
}

SearchColumnPolicy::~SearchColumnPolicy()
{
    // empty
}

void
SearchColumnPolicy::select(mbus::RoutingContext &context)
{
    std::vector<mbus::Route> recipients;
    context.getMatchedRecipients(recipients);
    if (recipients.empty()) {
        return;
    }
    const document::DocumentId *id = NULL;
    document::BucketId bucketId;

    const mbus::Message &msg = context.getMessage();
    switch(msg.getType()) {
    case DocumentProtocol::MESSAGE_PUTDOCUMENT:
	id = &static_cast<const PutDocumentMessage&>(msg).getDocument()->getId();
        break;

    case DocumentProtocol::MESSAGE_GETDOCUMENT:
	id = &static_cast<const GetDocumentMessage&>(msg).getDocumentId();
        break;

    case DocumentProtocol::MESSAGE_REMOVEDOCUMENT:
	id = &static_cast<const RemoveDocumentMessage&>(msg).getDocumentId();
        break;

    case DocumentProtocol::MESSAGE_UPDATEDOCUMENT:
	id = &static_cast<const UpdateDocumentMessage&>(msg).getDocumentUpdate()->getId();
	break;

    case DocumentProtocol::MESSAGE_MULTIOPERATION:
        bucketId = (static_cast<const MultiOperationMessage&>(msg)).getBucketId();
        break;

    case DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE:
        bucketId = (static_cast<const BatchDocumentUpdateMessage&>(msg)).getBucketId();
        break;

    default:
        LOG(error, "Message type '%d' not supported.", msg.getType());
	return;
    }
    if (bucketId.getRawId() == 0) {
        bucketId = _factory.getBucketId(*id);
    }
    uint32_t recipient = getRecipient(bucketId, recipients.size());
    context.addChild(recipients[recipient]);
    context.setSelectOnRetry(true);
    if (_maxOOS > 0) {
        context.addConsumableError(mbus::ErrorCode::SERVICE_OOS);
    }
}

void
SearchColumnPolicy::merge(mbus::RoutingContext &context)
{
    if (_maxOOS > 0) {
        if (context.getNumChildren() > 1) {
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
            if (oosReplies.size() <= _maxOOS) {
                DocumentProtocol::merge(context, oosReplies);
                return; // may the rtx be with you
            }
        } else {
            const mbus::Reply &ref = context.getChildIterator().getReplyRef();
            if (ref.hasErrors() && DocumentProtocol::hasOnlyErrorsOfType(ref, mbus::ErrorCode::SERVICE_OOS)) {
                context.setReply(mbus::Reply::UP(new mbus::EmptyReply()));
                return; // god help us all
            }
        }
    }
    DocumentProtocol::merge(context);
}

uint32_t
SearchColumnPolicy::getRecipient(const document::BucketId &bucketId, uint32_t numRecipients)
{
    vespalib::LockGuard guard(_lock);
    DistributionCache::iterator it = _distributions.find(numRecipients);
    if (it == _distributions.end()) {
        it = _distributions.insert(DistributionCache::value_type(numRecipients, vdslib::BucketDistribution(1, 16u))).first;
        it->second.setNumColumns(numRecipients);
    }
    return it->second.getColumn(bucketId);
}

}
