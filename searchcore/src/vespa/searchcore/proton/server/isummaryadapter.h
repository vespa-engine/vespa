// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/common/serialnum.h>
#include <memory>

namespace document {
    class Document;
    class DocumentTypeRepo;
}
namespace search { class IDocumentStore; }
namespace vespalib { class nbostream; }

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

    virtual ~ISummaryAdapter() {}

    // feed interface
    virtual void put(SerialNum serialNum, const DocumentIdT lid, const Document &doc) = 0;
    virtual void put(SerialNum serialNum, const DocumentIdT lid, const vespalib::nbostream & os) = 0;
    virtual void remove(SerialNum serialNum, const DocumentIdT lid) = 0;
    virtual void heartBeat(SerialNum serialNum) = 0;
    virtual const search::IDocumentStore &getDocumentStore() const = 0;
    virtual std::unique_ptr<Document> get(const DocumentIdT lid, const DocumentTypeRepo &repo) = 0;
    virtual void compactLidSpace(uint32_t wantedDocIdLimit) = 0;
};

} // namespace proton
