// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dociditerator.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/btree/btreenode.h>
#include <vespa/searchlib/btree/btreeiterator.h>

namespace search {

namespace fef {
    class TermFieldMatchData;
    class TermFieldMatchDataPosition;
}

/**
 * Abstract superclass for all attribute iterators with convenience function
 * for getting the type of the iterator (used for testing).
 **/
class AttributeIteratorBase : public queryeval::SearchIterator
{
protected:
    template <typename SC>
    void and_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const;
    template <typename SC>
    void or_hits_into(const SC & sc, BitVector & result, uint32_t begin_id) const;
    template <typename SC>
    std::unique_ptr<BitVector> get_hits(const SC & sc, uint32_t begin_id) const;
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
    AttributeIterator(fef::TermFieldMatchData * matchData)
        : AttributeIteratorBase(matchData),
          _weight(1)
    { }
protected:
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void doUnpack(uint32_t docId) override;
    int32_t    _weight;
};

class FilterAttributeIterator : public AttributeIteratorBase
{
public:
    FilterAttributeIterator(fef::TermFieldMatchData * matchData);
protected:
    void doUnpack(uint32_t docId) override;
};

template <typename SC>
class AttributeIteratorT : public AttributeIterator
{
private:
    void doSeek(uint32_t docId) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void and_hits_into(BitVector & result, uint32_t begin_id) override;
    void or_hits_into(BitVector & result, uint32_t begin_id) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;

protected:
    const SC & _searchContext;

public:
    AttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return _searchContext.matches(docId); }

    const attribute::ISearchContext * getAttributeSearchContext() const override { return &_searchContext; }
};

template <typename SC>
class FilterAttributeIteratorT : public FilterAttributeIterator
{
private:
    void doSeek(uint32_t docId) override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void and_hits_into(BitVector & result, uint32_t begin_id) override;
    void or_hits_into(BitVector & result, uint32_t begin_id) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;

protected:
    const SC & _searchContext;

public:
    FilterAttributeIteratorT(const SC &searchContext, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return _searchContext.matches(docId); }
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
    using AttributeIteratorT<SC>::_searchContext;
    using AttributeIteratorT<SC>::setDocId;
    using AttributeIteratorT<SC>::setAtEnd;
    using AttributeIteratorT<SC>::isAtEnd;
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
    using FilterAttributeIteratorT<SC>::_searchContext;
    using FilterAttributeIteratorT<SC>::setDocId;
    using FilterAttributeIteratorT<SC>::setAtEnd;
    using FilterAttributeIteratorT<SC>::isAtEnd;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
public:
    FilterAttributeIteratorStrict(const SC &searchContext, fef::TermFieldMatchData * matchData)
        : FilterAttributeIteratorT<SC>(searchContext, matchData)
    { }
};

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

    void initRange(uint32_t begin, uint32_t end) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;

public:
    template <typename... Args>
    AttributePostingListIteratorT(bool hasWeight, fef::TermFieldMatchData *matchData, Args &&... args);
};

template <typename PL>
class FilterAttributePostingListIteratorT
    : public FilterAttributePostingListIterator
{
private:
    PL                                     _iterator;
public:
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;

private:
    queryeval::MinMaxPostingInfo           _postingInfo;
    bool                                   _postingInfoValid;

    void doSeek(uint32_t docId) override;
    void doUnpack(uint32_t docId) override;
    void setupPostingInfo() { }

    const queryeval::PostingInfo * getPostingInfo() const override {
        return _postingInfoValid ? &_postingInfo : NULL;
    }

    void initRange(uint32_t begin, uint32_t end) override;

public:
    template <typename... Args>
    FilterAttributePostingListIteratorT(fef::TermFieldMatchData *matchData, Args &&... args);
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
    return 1;   // default weight 1 for single value attributes
}

template <>
void
AttributePostingListIteratorT<btree::BTreeConstIterator<uint32_t, btree::BTreeNoLeafData, btree::NoAggregated,
                              std::less<uint32_t>, btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId);


template <>
void
AttributePostingListIteratorT<btree::BTreeConstIterator<uint32_t, int32_t, btree::MinMaxAggregated,
                              std::less<uint32_t>, btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId);


template <>
void
AttributePostingListIteratorT<InnerAttributePostingListIterator>::setupPostingInfo();


template <>
void
AttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::setupPostingInfo();


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::setupPostingInfo();


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributeWeightPosting> >::setupPostingInfo();

template <>
void
FilterAttributePostingListIteratorT<InnerAttributePostingListIterator>::setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::setupPostingInfo();


template <>
void
FilterAttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::setupPostingInfo();

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
    using Attribute = typename SC::Attribute;
    void doSeek(uint32_t docId) override;

protected:
    const SC & _sc;

    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;

public:
    FlagAttributeIteratorT(const SC &sc, fef::TermFieldMatchData * matchData)
        : FlagAttributeIterator(matchData),
          _sc(sc)
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
    using FlagAttributeIteratorT<SC>::_sc;
    using FlagAttributeIteratorT<SC>::setDocId;
    using FlagAttributeIteratorT<SC>::setAtEnd;
    using FlagAttributeIteratorT<SC>::isAtEnd;
    using Attribute = typename SC::Attribute;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }

public:
    FlagAttributeIteratorStrict(const SC &sc, fef::TermFieldMatchData *matchData)
        : FlagAttributeIteratorT<SC>(sc, matchData)
    { }
};

} // namespace search
