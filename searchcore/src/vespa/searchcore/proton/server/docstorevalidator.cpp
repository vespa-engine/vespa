// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docstorevalidator.h"
#include "feedhandler.h"
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchcore/proton/common/feedtoken.h>
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>

#include <vespa/log/log.h>
LOG_SETUP(".server.docstorevalidator");

namespace proton {

DocStoreValidator::DocStoreValidator(IDocumentMetaStore &dms)
    : _dms(dms),
      _docIdLimit(dms.getCommittedDocIdLimit()),
      _invalid(search::BitVector::create(_docIdLimit)),
      _orphans(search::BitVector::create(_docIdLimit)),
      _visitCount(0u),
      _visitEmptyCount(0u)
{
    for (uint32_t lid = 1; lid < _docIdLimit; ++lid) {
        if (_dms.validLid(lid)) {
            _invalid->setBit(lid);
        }
    }
}


void
DocStoreValidator::visit(uint32_t lid, const std::shared_ptr<document::Document> &doc)
{
    if (lid == 0 || lid >= _docIdLimit)
        return;
    ++_visitCount;
    if (!_dms.validLid(lid)) {
        _orphans->setBit(lid);
        return;
    }
    const document::DocumentId &docId(doc->getId());
    const document::GlobalId &gid = docId.getGlobalId();
    const RawDocumentMetaData &meta = _dms.getRawMetaData(lid);
    const document::GlobalId &dmsGid = meta.getGid();
    if (gid == dmsGid) {
        _invalid->clearBit(lid);
    } else {
        _invalid->setBit(lid);
    }
}


void
DocStoreValidator::visit(uint32_t lid)
{
    if (lid == 0 || lid >= _docIdLimit)
        return;
    ++_visitEmptyCount;
    if (!_dms.validLid(lid)) {
        _orphans->clearBit(lid);
        return;
    }
    _invalid->setBit(lid);
}


void
DocStoreValidator::visitDone()
{
    _invalid->invalidateCachedCount();
    _orphans->invalidateCachedCount();
    (void) _invalid->countTrueBits();
    (void) _orphans->countTrueBits();
}

uint32_t
DocStoreValidator::getInvalidCount() const
{
    return _invalid->countTrueBits();
}

uint32_t
DocStoreValidator::getOrphanCount() const
{
    return _orphans->countTrueBits();
}

void
DocStoreValidator::killOrphans(search::IDocumentStore &store,
                               search::SerialNum serialNum)
{
    for (uint32_t lid = 1; lid < _docIdLimit; ++lid) {
        if (_orphans->testBit(lid)) {
            assert(!_dms.validLid(lid));
            store.remove(serialNum, lid);
        }
    }
}


std::shared_ptr<LidVectorContext>
DocStoreValidator::getInvalidLids() const
{
    auto res = std::make_unique<LidVectorContext>(_docIdLimit);
    assert(_invalid->size() == _docIdLimit);
    for (search::DocumentIdT lid(_invalid->getFirstTrueBit(1));
         lid < _docIdLimit;
         lid = _invalid->getNextTrueBit(lid + 1))
    {
        res->addLid(lid);
    }
    return res;
}

void
DocStoreValidator::performRemoves(FeedHandler & feedHandler, const search::IDocumentStore &store, const document::DocumentTypeRepo & repo) const {
    for (search::DocumentIdT lid(_invalid->getFirstTrueBit(1));
         lid < _docIdLimit;
         lid = _invalid->getNextTrueBit(lid + 1))
    {
        document::GlobalId gid;
        bool found = _dms.getGid(lid, gid);
        assert(found);
        if (found) {
            search::DocumentMetaData metaData = _dms.getMetaData(gid);
            assert(metaData.valid());
            document::Document::UP document = store.read(lid, repo);
            assert(document);
            LOG(info, "Removing document with id %s and lid %u with gid %s in bucket %s", document->getId().toString().c_str(), lid, metaData.gid.toString().c_str(), metaData.bucketId.toString().c_str());
            auto remove = std::make_unique<RemoveOperationWithGid>(metaData.bucketId, storage::spi::Timestamp(metaData.timestamp), gid, document->getType().getName());
            feedHandler.performOperation(FeedToken(), std::move(remove));
        }
    }
}

} // namespace proton
