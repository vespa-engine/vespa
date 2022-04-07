// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dociditerator.h"
#include "postinglisttraits.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchcommon/attribute/i_search_context.h>

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
    const attribute::ISearchContext & _baseSearchCtx;
    fef::TermFieldMatchData         * _matchData;
    fef::TermFieldMatchDataPosition * _matchPosition;

public:
    AttributeIteratorBase(const attribute::ISearchContext &baseSearchCtx, fef::TermFieldMatchData *matchData)
        : _baseSearchCtx(baseSearchCtx),
          _matchData(matchData),
          _matchPosition(_matchData->populate_fixed())
    { }
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
    AttributeIterator(const attribute::ISearchContext &baseSearchCtx,
                      fef::TermFieldMatchData *matchData)
        : AttributeIteratorBase(baseSearchCtx, matchData),
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
    FilterAttributeIterator(const attribute::ISearchContext &baseSearchCtx,
                            fef::TermFieldMatchData *matchData)
        : AttributeIteratorBase(baseSearchCtx, matchData)
    {
        _matchPosition->setElementWeight(1);
    }
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
    bool matches(uint32_t docId, int32_t &weight) const {
        return attribute::ISearchContext::matches(_concreteSearchCtx, docId, weight);
    }
    bool matches(uint32_t doc) const { return _concreteSearchCtx.find(doc, 0) >= 0; }
    const SC &_concreteSearchCtx;

public:
    AttributeIteratorT(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return matches(docId); }
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
    bool matches(uint32_t doc) const { return _concreteSearchCtx.find(doc, 0) >= 0; }
    const SC &_concreteSearchCtx;

public:
    FilterAttributeIteratorT(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData);
    bool seekFast(uint32_t docId) const { return matches(docId); }
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
    using AttributeIteratorT<SC>::_concreteSearchCtx;
    using AttributeIteratorT<SC>::setDocId;
    using AttributeIteratorT<SC>::setAtEnd;
    using AttributeIteratorT<SC>::isAtEnd;
    using AttributeIteratorT<SC>::_weight;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
public:
    AttributeIteratorStrict(const SC &concreteSearchCtx, fef::TermFieldMatchData * matchData)
        : AttributeIteratorT<SC>(concreteSearchCtx, matchData)
    { }
};


template <typename SC>
class FilterAttributeIteratorStrict : public FilterAttributeIteratorT<SC>
{
private:
    using FilterAttributeIteratorT<SC>::_concreteSearchCtx;
    using FilterAttributeIteratorT<SC>::setDocId;
    using FilterAttributeIteratorT<SC>::setAtEnd;
    using FilterAttributeIteratorT<SC>::isAtEnd;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }
public:
    FilterAttributeIteratorStrict(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData)
        : FilterAttributeIteratorT<SC>(concreteSearchCtx, matchData)
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
    AttributePostingListIterator(const attribute::ISearchContext &baseSearchCtx,
                                 bool hasWeight, fef::TermFieldMatchData *matchData)
        : AttributeIteratorBase(baseSearchCtx, matchData),
          _hasWeight(hasWeight)
    {}
    Trinary is_strict() const override { return Trinary::True; }
protected:
    bool  _hasWeight;
};


class FilterAttributePostingListIterator : public AttributeIteratorBase
{
public:
    FilterAttributePostingListIterator(const attribute::ISearchContext &baseSearchCtx, fef::TermFieldMatchData *matchData)
        : AttributeIteratorBase(baseSearchCtx, matchData)
    {}
    Trinary is_strict() const override { return Trinary::True; }
};


using InnerAttributePostingListIterator = attribute::PostingListTraits<vespalib::btree::BTreeNoLeafData>::const_iterator;

using WeightedInnerAttributePostingListIterator = attribute::PostingListTraits<int32_t>::const_iterator;

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
        return _postingInfoValid ? &_postingInfo : nullptr;
    }

    void initRange(uint32_t begin, uint32_t end) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;
    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;

public:
    template <typename... Args>
    AttributePostingListIteratorT(const attribute::ISearchContext &baseSearchCtx,
                                  bool hasWeight, fef::TermFieldMatchData *matchData,
                                  Args &&... args);
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
        return _postingInfoValid ? &_postingInfo : nullptr;
    }

    void initRange(uint32_t begin, uint32_t end) override;

public:
    template <typename... Args>
    FilterAttributePostingListIteratorT(const attribute::ISearchContext &baseSearchCtx, fef::TermFieldMatchData *matchData, Args &&... args);
};


template <>
inline int32_t
AttributePostingListIteratorT<InnerAttributePostingListIterator>::
getWeight()
{
    return 1;   // default weight 1 for single value attributes
}

template <>
void
AttributePostingListIteratorT<InnerAttributePostingListIterator >::
doUnpack(uint32_t docId);


template <>
void
AttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
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
    FlagAttributeIterator(const attribute::ISearchContext &baseSearchCtx, fef::TermFieldMatchData *matchData)
        : AttributeIteratorBase(baseSearchCtx, matchData)
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
    const SC &_concreteSearchCtx;

    void or_hits_into(BitVector &result, uint32_t begin_id) override;
    void and_hits_into(BitVector &result, uint32_t begin_id) override;
    std::unique_ptr<BitVector> get_hits(uint32_t begin_id) override;

public:
    FlagAttributeIteratorT(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData)
        : FlagAttributeIterator(concreteSearchCtx, matchData),
          _concreteSearchCtx(concreteSearchCtx)
    { }

    void initRange(uint32_t begin, uint32_t end) override {
        FlagAttributeIterator::initRange(begin, end);
        if ( _concreteSearchCtx._zeroHits ) {
            setAtEnd();
        }
    }

};

template <typename SC>
class FlagAttributeIteratorStrict : public FlagAttributeIteratorT<SC>
{
private:
    using FlagAttributeIteratorT<SC>::_concreteSearchCtx;
    using FlagAttributeIteratorT<SC>::setDocId;
    using FlagAttributeIteratorT<SC>::setAtEnd;
    using FlagAttributeIteratorT<SC>::isAtEnd;
    using Trinary=vespalib::Trinary;
    void doSeek(uint32_t docId) override;
    Trinary is_strict() const override { return Trinary::True; }

public:
    FlagAttributeIteratorStrict(const SC &concreteSearchCtx, fef::TermFieldMatchData *matchData)
        : FlagAttributeIteratorT<SC>(concreteSearchCtx, matchData)
    { }
};

}
