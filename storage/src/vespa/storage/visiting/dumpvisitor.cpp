// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dumpvisitor.h"
#include <vespa/documentapi/messagebus/messages/multioperationmessage.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/vdslib/container/mutabledocumentlist.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/hash_map.hpp>
#include <vespa/log/log.h>
LOG_SETUP(".visitor.instance.dumpvisitor");

namespace storage {

DumpVisitor::DumpVisitor(StorageComponent& component,
                         const vdslib::Parameters& params)
    : Visitor(component),
      _keepTimeStamps(false)
{
    if (params.hasValue("requestfields")) {
        std::string fields = params.get("requestfields");

        _requestedFields.reset(new std::set<std::string>());
        vespalib::StringTokenizer tokenizer(fields);
        for (uint32_t i = 0; i < tokenizer.size(); i++) {
            _requestedFields->insert(tokenizer[i]);
        }
    }

    if (params.hasValue("requestdocuments")) {
        std::string documents = params.get("requestdocuments");

        _requestedDocuments.reset(new std::set<std::string>());
        vespalib::StringTokenizer tokenizer(documents, " \t");
        for (uint32_t i = 0; i < tokenizer.size(); i++) {
            _requestedDocuments->insert(tokenizer[i]);
        }
    }

    if (params.hasValue("keeptimestamps")) {
	_keepTimeStamps = true;
    }

    LOG(debug, "Created DumpVisitor");
}

std::unique_ptr<documentapi::MultiOperationMessage>
DumpVisitor::createMultiOperation(const document::BucketId& bucketId,
                                  const std::vector<const document::Document*>& docs)
{
    for (int multiplier = 1; ; multiplier *= 2) {
        std::vector<char> buffer(getDocBlockSize() * multiplier);
        vdslib::MutableDocumentList newBlock(_component.getTypeRepo(),
                                             &buffer[0], buffer.size(), false);
        bool mustResizeBuffer = false;
        for (uint32_t i = 0; i < docs.size(); i++) {
            bool ok = newBlock.addPut(*docs[i], docs[i]->getLastModified());
            if (!ok) {
                mustResizeBuffer = true;
                break;
            }
        }

        if (!mustResizeBuffer) {
            return std::unique_ptr<documentapi::MultiOperationMessage>(
                    new documentapi::MultiOperationMessage(bucketId, newBlock, _keepTimeStamps));
        }
    }
    assert(false);
    return std::unique_ptr<documentapi::MultiOperationMessage>();
}

void DumpVisitor::handleDocuments(const document::BucketId& bucketId,
                                  std::vector<spi::DocEntry::UP>& entries,
                                  HitCounter& hitCounter)
{
    LOG(debug, "Visitor %s handling block of %zu documents.",
               _id.c_str(), entries.size());

    std::unique_ptr<documentapi::MultiOperationMessage> cmd;
    if (_requestedFields.get() || _requestedDocuments.get()) {
        std::vector<const document::Document*> newDocuments;

        // Remove all fields from the document that are not listed in
        // requestedFields.
        for (size_t i = 0; i < entries.size(); ++i) {
            std::unique_ptr<document::Document> d(entries[i]->getDocument()->clone());

            if (!_requestedDocuments.get()
                || _requestedDocuments->find(d->getId().toString())
                        != _requestedDocuments->end())
            {
                if (_requestedFields.get()) {
                    for (document::Document::const_iterator docIter
                            = d->begin(); docIter != d->end(); ++docIter)
                    {
                        if (_requestedFields->find(docIter.field().getName())
                                == _requestedFields->end())
                        {
                            d->remove(docIter.field());
                        }
                    }
                }
                newDocuments.push_back(d.release());
            }
        }

        cmd = createMultiOperation(bucketId, newDocuments);

        // FIXME: not exception safe
        for (uint32_t i = 0; i < newDocuments.size(); i++) {
            delete newDocuments[i];
        }
    } else {
        std::vector<const document::Document*> docs;
        docs.reserve(entries.size());
        for (size_t i = 0; i < entries.size(); ++i) {
            docs.push_back(entries[i]->getDocument());
            assert(docs.back() != 0);
        }
        cmd = createMultiOperation(bucketId, docs);
    }

    for (vdslib::DocumentList::const_iterator iter
            = cmd->getOperations().begin();
         iter != cmd->getOperations().end(); iter++)
    {
        hitCounter.addHit(iter->getDocumentId(), iter->getSerializedSize());
    }

    sendMessage(documentapi::DocumentMessage::UP(cmd.release()));
}

}
