// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/persistence/spi/types.h>
#include <vespa/searchlib/query/base.h>

namespace proton::test {

/**
 * Representation of a test document.
 */
class Document
{
private:
    document::Document::SP  _doc;
    search::DocumentIdT     _lid;
    storage::spi::Timestamp _tstamp;
    uint32_t                _numUsedBits;
public:
    Document(document::Document::SP doc,
             search::DocumentIdT lid,
             storage::spi::Timestamp tstamp,
             uint32_t numUsedBits = 8u)
        : _doc(doc),
          _lid(lid),
          _tstamp(tstamp),
          _numUsedBits(numUsedBits)
    {
    }
    const document::Document::SP &getDoc() const { return _doc; }
    const document::DocumentId &getDocId() const { return _doc->getId(); }
    const document::GlobalId &getGid() const { return getDocId().getGlobalId(); }
    document::BucketId getBucket() const {
        document::BucketId retval = getGid().convertToBucketId();
        retval.setUsedBits(_numUsedBits);
        return retval;
    }
    search::DocumentIdT getLid() const { return _lid; }
    storage::spi::Timestamp getTimestamp() const { return _tstamp; }
    uint32_t getDocSize() const { return 1000; }
};

typedef std::vector<Document> DocumentVector;

struct DocumentGidOrderCmp
{
    bool operator()(const Document &lhs, const Document &rhs) const {
        document::GlobalId::BucketOrderCmp cmp;
        return cmp(lhs.getGid(), rhs.getGid());
    }
};


}
