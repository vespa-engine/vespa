// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_context.h"
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/query/queryterm.h>
#include <vespa/vespalib/util/exceptions.h>

using document::GlobalId;
using search::AttributeIteratorBase;
using search::AttributeVector;
using search::QueryTermSimple;
using search::fef::TermFieldMatchData;
using search::queryeval::SearchIterator;

namespace proton::documentmetastore {

namespace {

class GidAllSearchIterator : public AttributeIteratorBase
{
private:
    void
    doSeek(uint32_t docId) override
    {
        if (_store.validLidFast(docId)) {
            setDocId(docId);
        }
    }

    void
    doUnpack(uint32_t docId) override
    {
        _matchData->reset(docId);
    }

protected:
    const DocumentMetaStore & _store;
public:
    GidAllSearchIterator(TermFieldMatchData *matchData, const DocumentMetaStore &store)
        : AttributeIteratorBase(matchData),
          _store(store)
    {
    }
};

class GidStrictAllSearchIterator : public GidAllSearchIterator
{
private:
    uint32_t _numDocs;

    virtual void
    doSeek(uint32_t docId) override
    {
        if (_store.validLidFast(docId)) {
            setDocId(docId);
        } else {
            for (docId++; docId < _numDocs && !_store.validLidFast(docId); docId++);
            if (docId < _numDocs) {
                setDocId(docId);
            } else {
                setAtEnd();
            }
        }
    }

public:
    GidStrictAllSearchIterator(TermFieldMatchData *matchData,
                               const DocumentMetaStore &store)
        : GidAllSearchIterator(matchData, store),
          _numDocs(store.getNumDocs())
    {
    }
};

class GidSearchIterator : public GidAllSearchIterator
{
private:
    const GlobalId & _gid;

    void
    doSeek(uint32_t docId) override
    {
        AttributeVector::DocId lid = 0;
        if (_store.getLid(_gid, lid) && (lid >= docId)) {
            setDocId(lid);
        } else {
            setAtEnd();
        }
    }
public:
    GidSearchIterator(TermFieldMatchData *matchData, const DocumentMetaStore &store, const GlobalId &gid)
        : GidAllSearchIterator(matchData, store),
          _gid(gid)
    {
    }
};

}

int32_t
SearchContext::onFind(DocId, int32_t, int32_t &) const
{
    throw vespalib::IllegalStateException("The function is not implemented for documentmetastore::SearchContext");
}

int32_t
SearchContext::onFind(DocId, int32_t ) const
{
    throw vespalib::IllegalStateException("The function is not implemented for documentmetastore::SearchContext");
}

unsigned int
SearchContext::approximateHits() const
{
    return _isWord ? 1 : search::AttributeVector::SearchContext::approximateHits();
}

SearchIterator::UP
SearchContext::createIterator(TermFieldMatchData *matchData, bool strict)
{
    return _isWord
        ? std::make_unique<GidSearchIterator>(matchData, getStore(), _gid)
        : strict
            ?  std::make_unique<GidStrictAllSearchIterator>(matchData, getStore())
            :  std::make_unique<GidAllSearchIterator>(matchData, getStore());
}

const DocumentMetaStore &
SearchContext::getStore() const
{
    return static_cast<const DocumentMetaStore &>(attribute());
}

SearchContext::SearchContext(QueryTermSimple::UP qTerm, const DocumentMetaStore &toBeSearched)
    : search::AttributeVector::SearchContext(toBeSearched),
      _isWord(qTerm->isWord())
{
}

}
