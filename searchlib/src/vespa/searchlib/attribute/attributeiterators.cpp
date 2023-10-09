// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributeiterators.hpp"
#include "postinglistattribute.h"

namespace search {

using queryeval::MinMaxPostingInfo;
using fef::TermFieldMatchData;

void
AttributeIteratorBase::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    SearchIterator::visitMembers(visitor);
    visit(visitor, "tfmd.fieldId", _matchData->getFieldId());
    visit(visitor, "tfmd.docId", _matchData->getDocId());
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
AttributePostingListIteratorT<InnerAttributePostingListIterator>::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
    _matchPosition->setElementWeight(getWeight());
}

template <>
void
AttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
    _matchPosition->setElementWeight(getWeight());
}

template <>
void
FilterAttributePostingListIteratorT<InnerAttributePostingListIterator>::
doUnpack(uint32_t docId)
{
    _matchData->resetOnlyDocId(docId);
}

template <>
void
FilterAttributePostingListIteratorT<WeightedInnerAttributePostingListIterator>::
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
        const vespalib::btree::MinMaxAggregated &a(_iterator.getAggregated());
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
        const vespalib::btree::MinMaxAggregated a(_iterator.getAggregated());
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
