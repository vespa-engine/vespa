// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "recoveryvisitor.h"
#include <vespa/persistence/spi/docentry.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/documentapi/messagebus/messages/visitor.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <utility>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.recoveryvisitor");

namespace storage {

RecoveryVisitor::RecoveryVisitor(StorageComponent& component,
                                 const vdslib::Parameters& params)
    : Visitor(component)
{
    if (params.hasValue("requestfields")) {
        std::string_view fields = params.get("requestfields");

        vespalib::StringTokenizer tokenizer(fields);
        for (auto field : tokenizer) {
            _requestedFields.emplace(field);
        }
    }


    LOG(debug, "Created RecoveryVisitor with %d requested fields", (int)_requestedFields.size());
}

void
RecoveryVisitor::handleDocuments(const document::BucketId& bid,
                                 DocEntryList & entries,
                                 HitCounter& hitCounter)
{
    std::lock_guard guard(_mutex);

    LOG(debug, "Visitor %s handling block of %zu documents.", _id.c_str(), entries.size());

    documentapi::DocumentListMessage* cmd = nullptr;

    {
        auto iter = _activeCommands.find(bid);

        if (iter == _activeCommands.end()) {
            CommandPtr ptr(new documentapi::DocumentListMessage(bid));
            cmd = ptr.get();
            _activeCommands[bid] = std::move(ptr);
        } else {
            cmd = iter->second.get();
        }
    }

    // Remove all fields from the document that are not listed in requestedFields.
    for (const auto & entrie : entries) {
        const spi::DocEntry& entry(*entrie);
        std::shared_ptr<document::Document> doc(entry.getDocument()->clone());
        if (_requestedFields.empty()) {
            doc->clear();
        } else {
            for (document::Document::const_iterator docIter = doc->begin();
                 docIter != doc->end();
                 ++docIter) {
                if (_requestedFields.find(docIter.field().getName())
                    == _requestedFields.end())
                {
                    doc->remove(docIter.field());
                }
            }
        }

        hitCounter.addHit(doc->getId(), doc->serialize().size());

        int64_t timestamp = doc->getLastModified();
        cmd->getDocuments().emplace_back(timestamp, std::move(doc), entry.isRemove());
    }
}

void RecoveryVisitor::completedBucket(const document::BucketId& bid, HitCounter&)
{
    documentapi::DocumentMessage::UP _msgToSend;

    LOG(debug, "Finished bucket %s", bid.toString().c_str());

    {
        std::lock_guard guard(_mutex);

        auto iter = _activeCommands.find(bid);

        if (iter != _activeCommands.end()) {
            _msgToSend = std::move(iter->second);
            _activeCommands.erase(iter);
        }
    }

    if (_msgToSend) {
        sendMessage(std::move(_msgToSend));
    }
}

}
