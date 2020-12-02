// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "reindexing_visitor.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/messagebus/messages/putdocumentmessage.h>
#include <vespa/storage/common/reindexing_constants.h>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.reindexing_visitor");

namespace storage {

ReindexingVisitor::ReindexingVisitor(StorageComponent& component)
    : Visitor(component)
{
}

void ReindexingVisitor::handleDocuments(const document::BucketId& /*bucketId*/,
                                        std::vector<spi::DocEntry::UP>& entries,
                                        HitCounter& hitCounter)
{
    LOG(debug, "Visitor %s handling block of %zu documents.", _id.c_str(), entries.size());
    for (auto& entry : entries) {
        if (entry->isRemove()) {
            // We don't reindex removed documents, as that would be very silly.
            continue;
        }
        const uint32_t doc_size = entry->getDocumentSize();
        hitCounter.addHit(*entry->getDocumentId(), doc_size);
        auto msg = std::make_unique<documentapi::PutDocumentMessage>(entry->releaseDocument());
        msg->setApproxSize(doc_size);
        msg->setCondition(documentapi::TestAndSetCondition(reindexing_bucket_lock_bypass_value()));
        sendMessage(std::move(msg));
    }
}

}
