// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "search_context.h"
#include "documentmetastore.h"
#include <vespa/searchlib/attribute/attributeiterators.h>
#include <vespa/searchlib/query/query_term_simple.h>
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
    GidAllSearchIterator(const search::attribute::ISearchContext &baseSearchCtx,
                         TermFieldMatchData *matchData, const DocumentMetaStore &store)
        : AttributeIteratorBase(baseSearchCtx, matchData),
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
    GidStrictAllSearchIterator(const search::attribute::ISearchContext &baseSearchCtx,
                               TermFieldMatchData *matchData,
                               const DocumentMetaStore &store)
        : GidAllSearchIterator(baseSearchCtx, matchData, store),
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
    GidSearchIterator(const search::attribute::ISearchContext &baseSearchCtx,
                      TermFieldMatchData *matchData, const DocumentMetaStore &store, const GlobalId &gid)
        : GidAllSearchIterator(baseSearchCtx, matchData, store),
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
    return _isWord ? 1 : search::attribute::SearchContext::approximateHits();
}

SearchIterator::UP
SearchContext::createIterator(TermFieldMatchData *matchData, bool strict)
{
    return _isWord
        ? std::make_unique<GidSearchIterator>(*this, matchData, getStore(), _gid)
        : strict
            ?  std::make_unique<GidStrictAllSearchIterator>(*this, matchData, getStore())
            :  std::make_unique<GidAllSearchIterator>(*this, matchData, getStore());
}

const DocumentMetaStore &
SearchContext::getStore() const
{
    return static_cast<const DocumentMetaStore &>(attribute());
}

SearchContext::SearchContext(QueryTermSimple::UP qTerm, const DocumentMetaStore &toBeSearched)
    : search::attribute::SearchContext(toBeSearched),
      _isWord(qTerm->isWord())
{
}

}
