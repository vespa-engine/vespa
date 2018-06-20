// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributeiterators.hpp"
#include "postinglistattribute.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attribute_iterators");

namespace search {

using queryeval::MinMaxPostingInfo;
using fef::TermFieldMatchData;

AttributeIteratorBase::AttributeIteratorBase(TermFieldMatchData * matchData) :
    _matchData(matchData),
    _matchPosition(_matchData->populate_fixed())
{ }

void
AttributeIteratorBase::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    SearchIterator::visitMembers(visitor);
    visit(visitor, "tfmd.fieldId", _matchData->getFieldId());
    visit(visitor, "tfmd.docId", _matchData->getDocId());
}

FilterAttributeIterator::FilterAttributeIterator(fef::TermFieldMatchData * matchData)
    : AttributeIteratorBase(matchData)
{
    _matchPosition->setElementWeight(1);
}

void
AttributeIterator::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeIteratorBase::visitMembers(visitor);
    visit(visitor, "weight", _weight);
}


void
FlagAttributeIterator::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}

AttributePostingListIterator:: AttributePostingListIterator(bool hasWeight, TermFieldMatchData *matchData)
    : AttributeIteratorBase(matchData),
      _hasWeight(hasWeight)
{
}

FilterAttributePostingListIterator::
FilterAttributePostingListIterator(TermFieldMatchData *matchData)
    : AttributeIteratorBase(matchData)
{
}

void
AttributeIterator::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
    _matchPosition->setElementWeight(_weight);
}


void
FilterAttributeIterator::doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}

template <>
void
AttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   btree::BTreeNoLeafData,
                   btree::NoAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
    _matchPosition->setElementWeight(getWeight());
}


template <>
void
AttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   int32_t,
                   btree::MinMaxAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
    _matchPosition->setElementWeight(getWeight());
}


template <>
void
FilterAttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   btree::BTreeNoLeafData,
                   btree::NoAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}


template <>
void
FilterAttributePostingListIteratorT<btree::
BTreeConstIterator<uint32_t,
                   int32_t,
                   btree::MinMaxAggregated,
                   std::less<uint32_t>,
                   btree::BTreeDefaultTraits> >::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}


template <>
void
AttributePostingListIteratorT<InnerAttributePostingListIterator>::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


template <>
void
AttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
setupPostingInfo()
{
    if (_iterator.valid()) {
        const btree::MinMaxAggregated &a(_iterator.getAggregated());
        _postingInfo = MinMaxPostingInfo(a.getMin(), a.getMax());
        _postingInfoValid = true;
    }
}


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


template <>
void
AttributePostingListIteratorT<DocIdMinMaxIterator<AttributeWeightPosting> >::
setupPostingInfo()
{
    if (_iterator.valid()) {
        const btree::MinMaxAggregated a(_iterator.getAggregated());
        _postingInfo = MinMaxPostingInfo(a.getMin(), a.getMax());
        _postingInfoValid = true;
    }
}

template <>
void
FilterAttributePostingListIteratorT<InnerAttributePostingListIterator>::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


template <>
void
FilterAttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


template <>
void
FilterAttributePostingListIteratorT<DocIdMinMaxIterator<AttributePosting> >::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


template <>
void
FilterAttributePostingListIteratorT<DocIdMinMaxIterator<AttributeWeightPosting> >::
setupPostingInfo()
{
    if (_iterator.valid()) {
        _postingInfo = MinMaxPostingInfo(1, 1);
        _postingInfoValid = true;
    }
}


} // namespace search
