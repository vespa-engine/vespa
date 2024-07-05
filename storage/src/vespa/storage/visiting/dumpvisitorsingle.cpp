// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dumpvisitorsingle.h"
#include <vespa/persistence/spi/docentry.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/documentapi/messagebus/messages/removedocumentmessage.h>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.dumpvisitorsingle");

namespace storage {

DumpVisitorSingle::DumpVisitorSingle(StorageComponent& component, const vdslib::Parameters&)
    : Visitor(component)
{
}

void DumpVisitorSingle::handleDocuments(const document::BucketId&,
                                        DocEntryList& entries,
                                        HitCounter& hitCounter)
{
    LOG(debug, "Visitor %s handling block of %zu documents.", _id.c_str(), entries.size());

    for (auto& entry : entries) {
        const uint32_t docSize = entry->getSize();
        if (entry->isRemove()) {
            hitCounter.addHit(*entry->getDocumentId(), docSize);
            auto msg = std::make_unique<documentapi::RemoveDocumentMessage>(*entry->getDocumentId());
            msg->set_persisted_timestamp(entry->getTimestamp());
            sendMessage(std::move(msg));
        } else {
            hitCounter.addHit(*entry->getDocumentId(), docSize);
            auto msg = std::make_unique<documentapi::PutDocumentMessage>(entry->releaseDocument());
            msg->setApproxSize(docSize);
            msg->set_persisted_timestamp(entry->getTimestamp());
            sendMessage(std::move(msg));
        }
    }
}

}
