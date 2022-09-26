// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "dociditerator.h"
#include "enumattribute.h"
#include "ipostinglistattributebase.h"
#include "numericbase.h"
#include "postingchange.h"
#include "postinglistsearchcontext.h"
#include "stringbase.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/btree/btreestore.h>
#include <vespa/vespalib/datastore/entry_comparator.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <map>

namespace search {

class EnumPostingPair
{
private:
    IEnumStore::Index _idx;
    const vespalib::datastore::EntryComparator *_cmp;
public:
    EnumPostingPair(IEnumStore::Index idx, const vespalib::datastore::EntryComparator *cmp)
        : _idx(idx),
          _cmp(cmp)
    { }

    bool operator<(const EnumPostingPair &rhs) const { return _cmp->less(_idx, rhs._idx); }
    IEnumStore::Index getEnumIdx() const { return _idx; }
};


template <typename P>
class PostingListAttributeBase : public attribute::IPostingListAttributeBase {
protected:
    using Posting = P;
    using DataType = typename Posting::DataType;

    using AggregationTraits = attribute::PostingListTraits<DataType>;
    using DocId = AttributeVector::DocId;
    using EntryRef = vespalib::datastore::EntryRef;
    using EnumIndex = IEnumStore::Index;
    using PostingList = typename AggregationTraits::PostingList;
    using PostingMap = std::map<EnumPostingPair, PostingChange<P> >;

    PostingList _postingList;
    AttributeVector &_attr;
    IEnumStoreDictionary& _dictionary;

    PostingListAttributeBase(AttributeVector &attr, IEnumStore &enumStore);
    ~PostingListAttributeBase() override;

    virtual void updatePostings(PostingMap & changePost) = 0;

    void updatePostings(PostingMap &changePost, const vespalib::datastore::EntryComparator &cmp);
    void clearAllPostings();
    void disableFreeLists() { _postingList.disableFreeLists(); }
    void disableElemHoldList() { _postingList.disableElemHoldList(); }
    void handle_load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader);
    bool forwardedOnAddDoc(DocId doc, size_t wantSize, size_t wantCapacity);

    void clearPostings(attribute::IAttributeVector::EnumHandle eidx, uint32_t fromLid,
                       uint32_t toLid, const vespalib::datastore::EntryComparator &cmp);

    void forwardedShrinkLidSpace(uint32_t newSize) override;
    vespalib::MemoryUsage getMemoryUsage() const override;
    bool consider_compact_worst_btree_nodes(const CompactionStrategy& compaction_strategy) override;
    bool consider_compact_worst_buffers(const CompactionStrategy& compaction_strategy) override;

public:
    const PostingList & getPostingList() const { return _postingList; }
    PostingList & getPostingList()             { return _postingList; }
};

template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
class PostingListAttributeSubBase : public PostingListAttributeBase<P> {
public:
    using Parent = PostingListAttributeBase<P>;

    using Dictionary = EnumPostingTree;
    using EntryRef = vespalib::datastore::EntryRef;
    using EnumIndex = IEnumStore::Index;
    using EnumStore = EnumStoreType;
    using ComparatorType = typename EnumStore::ComparatorType;
    using PostingList = typename Parent::PostingList;
    using PostingMap = typename Parent::PostingMap;

    using Parent::clearAllPostings;
    using Parent::updatePostings;
    using Parent::handle_load_posting_lists_and_update_enum_store;
    using Parent::clearPostings;
    using Parent::_postingList;
    using Parent::_attr;
    using Parent::_dictionary;

private:
    EnumStore &_es;

public:
    PostingListAttributeSubBase(AttributeVector &attr, EnumStore &enumStore);
    ~PostingListAttributeSubBase() override;

    void handle_load_posting_lists(LoadedVector &loaded);
    void updatePostings(PostingMap &changePost) override;
    void clearPostings(attribute::IAttributeVector::EnumHandle eidx, uint32_t fromLid, uint32_t toLid) override;
};

extern template class PostingListAttributeBase<AttributePosting>;
extern template class PostingListAttributeBase<AttributeWeightPosting>;

} // namespace search
