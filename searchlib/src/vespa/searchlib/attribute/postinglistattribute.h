// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/numericbase.h>
#include <vespa/searchlib/attribute/stringbase.h>
#include <vespa/searchlib/attribute/enumattribute.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/datastore/entryref.h>
#include <vespa/searchlib/btree/btreestore.h>
#include "dociditerator.h"
#include "postinglistsearchcontext.h"
#include "postingchange.h"
#include "ipostinglistattributebase.h"

namespace search {

class EnumPostingPair
{
private:
    EnumStoreBase::Index _idx;
    const EnumStoreComparator *_cmp;
public:
    EnumPostingPair(EnumStoreBase::Index idx, const EnumStoreComparator *cmp)
        : _idx(idx),
          _cmp(cmp)
    { }

    bool operator<(const EnumPostingPair &rhs) const { return (*_cmp)(_idx, rhs._idx); }
    EnumStoreBase::Index getEnumIdx() const { return _idx; }
};


template <typename P>
class PostingListAttributeBase : public attribute::IPostingListAttributeBase
{
protected:
    typedef P Posting;
    typedef typename Posting::DataType DataType;
    typedef attribute::PostingListTraits<DataType> AggregationTraits;
    typedef typename AggregationTraits::PostingList PostingList;
    typedef AttributeVector::DocId DocId;
    typedef std::map<EnumPostingPair, PostingChange<P> > PostingMap;
    typedef datastore::EntryRef EntryRef;
    typedef attribute::LoadedEnumAttributeVector  LoadedEnumAttributeVector;
    typedef EnumStoreBase::Index EnumIndex;
    PostingList _postingList;
    AttributeVector &_attr;
    EnumPostingTree &_dict;
    EnumStoreBase   &_esb;

    PostingListAttributeBase(AttributeVector &attr, EnumStoreBase &enumStore);
    virtual ~PostingListAttributeBase();

    virtual void updatePostings(PostingMap & changePost) = 0;

    void updatePostings(PostingMap &changePost, EnumStoreComparator &cmp);
    void clearAllPostings();
    void disableFreeLists() { _postingList.disableFreeLists(); }
    void disableElemHoldList() { _postingList.disableElemHoldList(); }
    void fillPostingsFixupEnumBase(const LoadedEnumAttributeVector &loaded);
    bool forwardedOnAddDoc(DocId doc, size_t wantSize, size_t wantCapacity);

    void clearPostings(attribute::IAttributeVector::EnumHandle eidx, uint32_t fromLid,
                       uint32_t toLid, EnumStoreComparator &cmp);

    void forwardedShrinkLidSpace(uint32_t newSize) override;

public:
    const PostingList & getPostingList() const { return _postingList; }
    PostingList & getPostingList()             { return _postingList; }
};

template <typename P, typename LoadedVector, typename LoadedValueType,
          typename EnumStoreType>
class PostingListAttributeSubBase : public PostingListAttributeBase<P>
{
public:
    typedef PostingListAttributeBase<P> Parent;
    typedef EnumStoreType EnumStore;
    typedef EnumPostingTree Dictionary;
    typedef typename Dictionary::Iterator DictionaryIterator;
    typedef EnumStoreBase::Index EnumIndex;
    typedef typename EnumStore::FoldedComparatorType FoldedComparatorType;
    typedef datastore::EntryRef EntryRef;
    typedef typename Parent::PostingMap PostingMap;
    typedef typename Parent::PostingList PostingList;
    typedef typename PostingList::Iterator PostingIterator;
    typedef attribute::LoadedEnumAttributeVector LoadedEnumAttributeVector;

    using Parent::clearAllPostings;
    using Parent::updatePostings;
    using Parent::fillPostingsFixupEnumBase;
    using Parent::clearPostings;
    using Parent::_postingList;
    using Parent::_attr;
    using Parent::_dict;

private:
    EnumStore &_es;

public:
    PostingListAttributeSubBase(AttributeVector &attr, EnumStore &enumStore);
    virtual ~PostingListAttributeSubBase();

    void handleFillPostings(LoadedVector &loaded);
    void updatePostings(PostingMap &changePost) override;
    void printPostingListContent(vespalib::asciistream & os) const;
    void clearPostings(attribute::IAttributeVector::EnumHandle eidx, uint32_t fromLid, uint32_t toLid) override;
};

extern template class PostingListAttributeBase<AttributePosting>;
extern template class PostingListAttributeBase<AttributeWeightPosting>;

} // namespace search
