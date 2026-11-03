// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/query/base.h>

#include <memory>

namespace document {
class Document;
class DocumentTypeRepo;
} // namespace document
namespace search {
class IDocumentStore;
}
namespace vespalib {
class nbostream;
}

namespace proton {

/**
 * Interface for a summary adapter.
 **/
class ISummaryAdapter {
public:
    using UP = std::unique_ptr<ISummaryAdapter>;
    using SP = std::shared_ptr<ISummaryAdapter>;
    using SerialNum = search::SerialNum;
    using Document = document::Document;
    using DocumentIdT = search::DocumentIdT;
    using DocumentTypeRepo = document::DocumentTypeRepo;

    virtual ~ISummaryAdapter() = default;

    // feed interface
    virtual void put(SerialNum serialNum, const DocumentIdT lid, const Document& doc) = 0;
    virtual void put(SerialNum serialNum, const DocumentIdT lid, const vespalib::nbostream& os) = 0;
    virtual void remove(SerialNum serialNum, const DocumentIdT lid) = 0;
    /*
     * Same as put(), but for a document moved from prevLid to lid within the same document store
     * (e.g. by lid space compaction), allowing a cached copy of the document at prevLid to be re-keyed
     * to lid instead of being lost. The document at prevLid is not removed.
     */
    virtual void move(SerialNum serialNum, const DocumentIdT, const DocumentIdT lid, const Document& doc) {
        put(serialNum, lid, doc);
    }
    virtual void heartBeat(SerialNum serialNum) = 0;
    virtual const search::IDocumentStore& getDocumentStore() const = 0;
    virtual std::unique_ptr<Document> get(const DocumentIdT lid, const DocumentTypeRepo& repo) = 0;
    virtual void compactLidSpace(uint32_t wantedDocIdLimit) = 0;
};

} // namespace proton
