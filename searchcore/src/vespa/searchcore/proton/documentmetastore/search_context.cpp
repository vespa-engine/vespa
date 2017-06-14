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

namespace proton {
namespace documentmetastore {

namespace {

class GidAllSearchIterator : public AttributeIteratorBase
{
private:
    virtual void
    doSeek(uint32_t docId) override
    {
        if (_store.validLidFast(docId)) {
            setDocId(docId);
        }
    }

    virtual void
    doUnpack(uint32_t docId) override
    {
        _matchData->reset(docId);
    }

protected:
    const DocumentMetaStore & _store;
public:
    GidAllSearchIterator(TermFieldMatchData *matchData,
                         const DocumentMetaStore &store)
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

    virtual void
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
    GidSearchIterator(TermFieldMatchData *matchData,
                      const DocumentMetaStore &store,
                      const GlobalId &gid)
        : GidAllSearchIterator(matchData, store),
          _gid(gid)
    {
    }
};

}

bool
SearchContext::onCmp(DocId docId, int32_t &weight) const
{
    (void) docId;
    (void) weight;
    throw vespalib::IllegalStateException(
            "The function is not implemented for documentmetastore::SearchContext");
    return false;
}

bool
SearchContext::onCmp(DocId docId) const
{
    (void) docId;
    throw vespalib::IllegalStateException(
            "The function is not implemented for documentmetastore::SearchContext");
    return false;
}

unsigned int
SearchContext::approximateHits() const
{
    return _isWord ? 1 : search::AttributeVector::SearchContext::approximateHits();
}

SearchIterator::UP
SearchContext::createIterator(TermFieldMatchData *matchData,
                              bool strict)
{
    return _isWord
        ? SearchIterator::UP(new GidSearchIterator(matchData, getStore(), _gid))
        : strict
            ?  SearchIterator::UP(new GidStrictAllSearchIterator(matchData,
                                      getStore()))
            :  SearchIterator::UP(new GidAllSearchIterator(matchData, getStore()));
}

const DocumentMetaStore &
SearchContext::getStore() const
{
    return static_cast<const DocumentMetaStore &>(attribute());
}

SearchContext::SearchContext(QueryTermSimple::UP qTerm,
                             const DocumentMetaStore &toBeSearched)
    : search::AttributeVector::SearchContext(toBeSearched),
      _isWord(qTerm->isWord())
{
}

}
}
