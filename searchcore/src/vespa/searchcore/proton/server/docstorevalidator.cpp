// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docstorevalidator.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/document/fieldvalue/document.h>

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


LidVectorContext::SP
DocStoreValidator::getInvalidLids() const
{
    LidVectorContext::SP res(new LidVectorContext(_docIdLimit));
    assert(_invalid->size() == _docIdLimit);
    for (search::DocumentIdT lid(_invalid->getFirstTrueBit(1));
         lid < _docIdLimit;
         lid = _invalid->getNextTrueBit(lid + 1)) {

        res->addLid(lid);
    }
    return res;
}


} // namespace proton
