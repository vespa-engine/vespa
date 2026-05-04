// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docstorevalidator.h"

#include "feedhandler.h"

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>
#include <vespa/searchcore/proton/feedoperation/noopoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchlib/common/bitvector.h>

#include <vespa/log/log.h>
LOG_SETUP(".server.docstorevalidator");

namespace proton {

DocStoreValidator::DocStoreValidator(IDocumentMetaStore& dms)
    : _dms(dms),
      _docIdLimit(dms.getCommittedDocIdLimit()),
      _invalid(search::BitVector::create(_docIdLimit)),
      _orphans(search::BitVector::create(_docIdLimit)),
      _visitCount(0u),
      _visitEmptyCount(0u),
      _updated_doc_id(false) {
    for (uint32_t lid = 1; lid < _docIdLimit; ++lid) {
        if (_dms.validLid(lid)) {
            _invalid->setBit(lid);
        }
    }
}

DocStoreValidator::~DocStoreValidator() = default;

void DocStoreValidator::visit(uint32_t lid, const std::shared_ptr<document::Document>& doc) {
    if (lid == 0 || lid >= _docIdLimit)
        return;
    ++_visitCount;
    if (!_dms.validLid(lid)) {
        _orphans->setBit(lid);
        return;
    }
    const document::DocumentId& docId(doc->getId());
    const document::GlobalId&   gid = docId.getGlobalId();
    const RawDocumentMetadata&  meta = _dms.getRawMetadata(lid);
    const document::GlobalId&   dmsGid = meta.getGid();
    if (gid == dmsGid) {
        _invalid->clearBit(lid);
    } else {
        _invalid->setBit(lid);
    }

    if (_dms.can_populate_document_metadata_docid()) {
        // DocumentMetaStore should store the document id of this document
        // If it does not have the document id, we add it.
        // If it does have the document id, we verify that it matches the one from doc.
        auto metadata = _dms.getMetadata(dmsGid);
        if (metadata.docid.empty()) {
            // document id is missing, we have to add it
            _dms.update_docid_string(lid, doc->getId().toString());
            _updated_doc_id = true;
        } else if (metadata.docid != doc->getId().toString()) {
            // document id is there but does not match the one from the docstore
            _invalid->setBit(lid);
        }
    }
}

void DocStoreValidator::visit(uint32_t lid) {
    if (lid == 0 || lid >= _docIdLimit)
        return;
    ++_visitEmptyCount;
    if (!_dms.validLid(lid)) {
        _orphans->clearBit(lid);
        return;
    }
    _invalid->setBit(lid);
}

void DocStoreValidator::visitDone() {
    _invalid->invalidateCachedCount();
    _orphans->invalidateCachedCount();
    (void)_invalid->countTrueBits();
    (void)_orphans->countTrueBits();
}

uint32_t DocStoreValidator::getInvalidCount() const {
    return _invalid->countTrueBits();
}

uint32_t DocStoreValidator::getOrphanCount() const {
    return _orphans->countTrueBits();
}

void DocStoreValidator::killOrphans(search::IDocumentStore& store, search::SerialNum serialNum) {
    for (uint32_t lid = 1; lid < _docIdLimit; ++lid) {
        if (_orphans->testBit(lid)) {
            assert(!_dms.validLid(lid));
            store.remove(serialNum, lid);
        }
    }
}

std::shared_ptr<LidVectorContext> DocStoreValidator::getInvalidLids() const {
    auto res = std::make_unique<LidVectorContext>(_docIdLimit);
    assert(_invalid->size() == _docIdLimit);
    for (search::DocumentIdT lid(_invalid->getFirstTrueBit(1)); lid < _docIdLimit;
         lid = _invalid->getNextTrueBit(lid + 1))
    {
        res->addLid(lid);
    }
    return res;
}

void DocStoreValidator::performRemoves(FeedHandler& feedHandler, const search::IDocumentStore& store,
                                       const document::DocumentTypeRepo& repo) const {
    for (search::DocumentIdT lid(_invalid->getFirstTrueBit(1)); lid < _docIdLimit;
         lid = _invalid->getNextTrueBit(lid + 1))
    {
        document::GlobalId gid;
        bool               found = _dms.getGid(lid, gid);
        assert(found);
        if (found) {
            search::DocumentMetadata metadata = _dms.getMetadata(gid);
            assert(metadata.valid());
            document::Document::UP document = store.read(lid, repo);
            assert(document);
            LOG(info, "Removing document with id %s and lid %u with gid %s in bucket %s",
                document->getId().toString().c_str(), lid, metadata.gid.toString().c_str(),
                metadata.bucketId.toString().c_str());
            auto remove = std::make_unique<RemoveOperationWithGid>(
                metadata.bucketId, storage::spi::Timestamp(metadata.timestamp), gid, document->getType().getName());
            feedHandler.performOperation(FeedToken(), std::move(remove));
        }
    }
}

void DocStoreValidator::increase_serial_number_if_necessary(FeedHandler& feedHandler) const {
    if (_updated_doc_id) {
        NoopOperation op;
        op.setSerialNum(feedHandler.inc_serial_num());
        (void)feedHandler.storeOperationSync(op);
        feedHandler.syncTls(op.getSerialNum());
    }
}

} // namespace proton
