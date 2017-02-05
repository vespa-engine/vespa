// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dociditerator.h"
#include "attributevector.h"
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdataposition.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/btree/btreenode.h>
#include <vespa/searchlib/btree/btreeiterator.h>
#include <vespa/vespalib/objects/visit.h>

namespace search {

/**
 * Abstract superclass for all attribute iterators with convenience function
 * for getting the type of the iterator (used for testing).
 **/
class AttributeIteratorBase : public queryeval::SearchIterator
{
protected:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    fef::TermFieldMatchData * _matchData;
    fef::TermFieldMatchDataPosition * _matchPosition;

public:
    AttributeIteratorBase(fef::TermFieldMatchData * matchData);
    Trinary is_strict() const override { return Trinary::False; }
};


/**
 * This class acts as an iterator over documents that are results for
 * the subquery represented by the search context object associated
 * with this iterator.  The search context object contains an
 * attribute vector that does not use posting lists.
 *
 * @param SC the specialized search context type associated with this iterator
 */

class AttributeIterator : public AttributeIteratorBase
{
public:
    AttributeIterator(fef::TermFieldMatchData * matchData, uint32_t docIdLimit)
        : AttributeIteratorBase(matchData),
          _docIdLimit(docIdLimit),
          _weight(1)
    {
    }
protected:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doUnpack(uint32_t docId) override;
    uint32_t   _docIdLimit;
    int32_t    _weight;
};

class FilterAttributeIterator : public AttributeIteratorBase
{
public:
    FilterAttributeIterator(fef::TermFieldMatchData * matchData, uint32_t docIdLimit)
        : AttributeIteratorBase(matchData),
          _docIdLimit(docIdLimit)
    {
        _matchPosition->setElementWeight(1);
    }
protected:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doUnpack(uint32_t docId) override;
    uint32_t   _docIdLimit;
};

template <typename SC>
class AttributeIteratorT : public AttributeIterator
{
private:
    void doSeek(uint32_t docId) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

protected:
    const SC & _searchContext;

public:
    AttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return _searchContext.cmp(docId); }
};


template <typename SC>
class FilterAttributeIteratorT : public FilterAttributeIterator
{
private:
    void doSeek(uint32_t docId) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

protected:
    const SC & _searchContext;

public:
    FilterAttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return _searchContext.cmp(docId); }
};


/**
 * This class acts as a strict iterator over documents that are
 * results for the subquery represented by the search context object
 * associated with this iterator.  The search context object contains
 * an attribute vector that does not use posting lists.
 *
 * @param SC the specialized search context type associated with this iterator
 */
template <typename SC>
class AttributeIteratorStrict : public AttributeIteratorT<SC>
{
private:
    using AttributeIteratorT<SC>::_docIdLimit;
    using AttributeIteratorT<SC>::_searchContext;
    using AttributeIteratorT<SC>::setDocId;
    using AttributeIteratorT<SC>::setAtEnd;
    using AttributeIteratorT<SC>::_weight;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
public:
    AttributeIteratorStrict(const SC &searchContext, fef::TermFieldMatchData * matchData)
        : AttributeIteratorT<SC>(searchContext, matchData)
    { }
};


template <typename SC>
class FilterAttributeIteratorStrict : public FilterAttributeIteratorT<SC>
{
private:
    using FilterAttributeIteratorT<SC>::_docIdLimit;
    using FilterAttributeIteratorT<SC>::_searchContext;
    using FilterAttributeIteratorT<SC>::setDocId;
    using FilterAttributeIteratorT<SC>::setAtEnd;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
public:
    FilterAttributeIteratorStrict(const SC &searchContext, fef::TermFieldMatchData * matchData)
        : FilterAttributeIteratorT<SC>(searchContext, matchData)
    { }
};


template <typename SC>
void
AttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (_searchContext.cmp(docId, _weight)) {
        setDocId(docId);
    }
}

template <typename SC>
void
FilterAttributeIteratorT<SC>::doSeek(uint32_t docId)
{
    if (__builtin_expect(docId >= _docIdLimit, false)) {
        setAtEnd();
    } else if (_searchContext.cmp(docId)) {
        setDocId(docId);
    }
}

template <typename SC>
void
AttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; nextId < _docIdLimit; ++nextId) {
        if (_searchContext.cmp(nextId, _weight)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

template <typename SC>
void
FilterAttributeIteratorStrict<SC>::doSeek(uint32_t docId)
{
    for (uint32_t nextId = docId; nextId < _docIdLimit; ++nextId) {
        if (_searchContext.cmp(nextId)) {
            setDocId(nextId);
            return;
        }
    }
    setAtEnd();
}

/**
 * This class acts as an iterator over documents that are results for
 * the subquery represented by the search context object associated
 * with this iterator.  The search context object contains an
 * attribute vector that uses underlying posting lists, and the search
 * context will setup a posting list iterator which is used by this
 * class.  This iterator is always strict.
 *
 * @param PL the posting list iterator type to work as an iterator over
 */
class AttributePostingListIterator : public AttributeIteratorBase
{
public:
    AttributePostingListIterator(bool hasWeight, fef::TermFieldMatchData *matchData);
    Trinary is_strict() const override { return Trinary::True; }
protected:
    bool  _hasWeight;
};


class FilterAttributePostingListIterator : public AttributeIteratorBase
{
public:
    FilterAttributePostingListIterator(fef::TermFieldMatchData *matchData);
    Trinary is_strict() const override { return Trinary::True; }
};


typedef btree::BTreeConstIterator<uint32_t,
                                  btree::BTreeNoLeafData,
                                  btree::NoAggregated,
                                  std::less<uint32_t>,
                                  btree::BTreeDefaultTraits>
InnerAttributePostingListIterator;

typedef btree::BTreeConstIterator<uint32_t,
                                  int32_t,
                                  btree::MinMaxAggregated,
                                  std::less<uint32_t>,
                                  btree::BTreeDefaultTraits>
WeightedInnerAttributePostingListIterator; 

template <typename PL>
class AttributePostingListIteratorT : public AttributePostingListIterator
{
private:
    PL                                     _iterator;
    queryeval::MinMaxPostingInfo           _postingInfo;
    bool                                   _postingInfoValid;

    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    void setupPostingInfo() { }
    int32_t getWeight() { return _iterator.getData(); }

    const queryeval::PostingInfo * getPostingInfo() const override {
        return _postingInfoValid ? &_postingInfo : NULL;
    }

    void initRange(uint32_t begin, uint32_t end) override {
        AttributePostingListIterator::initRange(begin, end);
        _iterator.lower_bound(begin);
        if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
            setAtEnd();
        } else {
            setDocId(_iterator.getKey());
        }
    }

public:
    // Note: iterator constructor argument is destroyed
    AttributePostingListIteratorT(PL &iterator,
                                  bool hasWeight,
                                  fef::TermFieldMatchData *matchData);
};

template <typename PL>
class FilterAttributePostingListIteratorT
    : public FilterAttributePostingListIterator
{
private:
    PL                                     _iterator;
public:
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;

private:
    queryeval::MinMaxPostingInfo           _postingInfo;
    bool                                   _postingInfoValid;

    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    void setupPostingInfo() { }

    const queryeval::PostingInfo * getPostingInfo() const override {
        return _postingInfoValid ? &_postingInfo : NULL;
    }
    
    void initRange(uint32_t begin, uint32_t end) override {
        FilterAttributePostingListIterator::initRange(begin, end);
        _iterator.lower_bound(begin);
        if (!_iterator.valid() || isAtEnd(_iterator.getKey())) {
            setAtEnd();
        } else {
            setDocId(_iterator.getKey());
        }
    }

public:
    // Note: iterator constructor argument is destroyed
    FilterAttributePostingListIteratorT(PL &iterator, fef::TermFieldMatchData *matchData);
};


template <>
inline int32_t
AttributePostingListIteratorT<
    btree::BTreeConstIterator<uint32_t,
                              btree::BTreeNoLeafData,
                              btree::NoAggregated,
                              std::less<uint32_t>,
                              btree::BTreeDefaultTraits> >::
getWeight()
{
    return 1;	// default weight 1 for single value attributes
}

template <>
void
AttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   btree::BTreeNoLeafData,
                   btree::NoAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId);


template <>
void
AttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   int32_t,
                   btree::MinMaxAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId);


template <>
void
AttributePostingListIteratorT<InnerAttributePostingListIterator>::
setupPostingInfo();


template <>
void
AttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
setupPostingInfo();


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::
setupPostingInfo();


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributeWeightPosting> >::
setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<InnerAttributePostingListIterator>::
setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::
setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<DocIdMinMaxIterator<AttributeWeightPosting> >::
setupPostingInfo();


template <typename PL>
AttributePostingListIteratorT<PL>::
AttributePostingListIteratorT(PL &iterator,
                              bool hasWeight,
                              fef::TermFieldMatchData *matchData)
    : AttributePostingListIterator(hasWeight, matchData),
      _iterator(),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    _iterator.swap(iterator);
    setupPostingInfo();
}


template <typename PL>
FilterAttributePostingListIteratorT<PL>::
FilterAttributePostingListIteratorT(PL &iterator, fef::TermFieldMatchData *matchData)
    : FilterAttributePostingListIterator(matchData),
      _iterator(),
      _postingInfo(1, 1),
      _postingInfoValid(false)
{
    _iterator.swap(iterator);
    setupPostingInfo();
    _matchPosition->setElementWeight(1);
}


/**
 * This class acts as an iterator over a flag attribute.
 */
class FlagAttributeIterator : public AttributeIteratorBase
{
public:
    FlagAttributeIterator(fef::TermFieldMatchData * matchData)
        : AttributeIteratorBase(matchData)
    { }
protected:
    void doUnpack(uint32_t docId) override;
};

template <typename SC>
class FlagAttributeIteratorT : public FlagAttributeIterator
{
private:
    void doSeek(uint32_t docId) override;

protected:
    const SC & _sc;
    uint32_t   _docIdLimit;

public:
    FlagAttributeIteratorT(const SC &sc, fef::TermFieldMatchData * matchData)
        : FlagAttributeIterator(matchData),
          _sc(sc),
          _docIdLimit(static_cast<const typename SC::Attribute &>
                      (sc.attribute()).getCommittedDocIdLimit())
    { }

    void initRange(uint32_t begin, uint32_t end) override {
        FlagAttributeIterator::initRange(begin, end);
        if ( _sc._zeroHits ) {
            setAtEnd();
        }
    }

};

template <typename SC>
class FlagAttributeIteratorStrict : public FlagAttributeIteratorT<SC>
{
private:
    using FlagAttributeIteratorT<SC>::_docIdLimit;
    using FlagAttributeIteratorT<SC>::_sc;
    using FlagAttributeIteratorT<SC>::setDocId;
    using FlagAttributeIteratorT<SC>::setAtEnd;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }

public:
    FlagAttributeIteratorStrict(const SC &sc, fef::TermFieldMatchData *matchData)
        : FlagAttributeIteratorT<SC>(sc, matchData)
    { }
};

} // namespace search

