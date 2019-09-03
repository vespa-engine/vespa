// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlenumericenumattribute.h"
#include "postinglistattribute.h"
#include "postinglistsearchcontext.h"

namespace search {

/**
 * Implementation of single value numeric attribute that in addition to enum store
 * uses an underlying posting list to provide faster search.
 *
 * B: EnumAttribute<BaseClass>
 */
template <typename B>
class SingleValueNumericPostingAttribute
    : public SingleValueNumericEnumAttribute<B>,
      protected PostingListAttributeSubBase<AttributePosting,
                                            typename B::LoadedVector,
                                            typename B::LoadedValueType,
                                            typename B::EnumStore>
{
public:
    using T = typename SingleValueNumericEnumAttribute<B>::T;
    using Dictionary = EnumPostingTree;
    using EnumStore = typename SingleValueNumericEnumAttribute<B>::EnumStore;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

private:
    friend class PostingListAttributeTest;
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()

    using LoadedVector = typename B::LoadedVector;
    using PostingParent = PostingListAttributeSubBase<AttributePosting,
                                                      LoadedVector,
                                                      typename B::LoadedValueType,
                                                      typename B::EnumStore>;

    using Change = typename B::BaseClass::Change;
    using ComparatorType = typename EnumStore::ComparatorType;
    using DocId = typename B::BaseClass::DocId;
    using EnumIndex = typename SingleValueEnumAttributeBase::EnumIndex;
    using LoadedEnumAttributeVector = attribute::LoadedEnumAttributeVector;
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SelfType = SingleValueNumericPostingAttribute<B>;
    using SingleSearchContext = typename SingleValueNumericEnumAttribute<B>::SingleSearchContext;
    using SingleNumericSearchContext = SingleSearchContext;
    using SinglePostingSearchContext = attribute::NumericPostingSearchContext<SingleNumericSearchContext, SelfType, btree::BTreeNoLeafData>;
    using ValueModifier = typename B::BaseClass::ValueModifier;
    using generation_t = typename SingleValueNumericEnumAttribute<B>::generation_t;

    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handleFillPostings;
    using PostingParent::fillPostingsFixupEnumBase;
    using PostingParent::forwardedOnAddDoc;

    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c, EnumStore & enumStore,
                                std::map<DocId, EnumIndex> & currEnumIndices);
    void makePostingChange(const datastore::EntryComparator *cmp,
                           const std::map<DocId, EnumIndex> &currEnumIndices,
                           PostingMap &changePost);

    void applyValueChanges(EnumStoreBatchUpdater& updater) override;

public:
    SingleValueNumericPostingAttribute(const vespalib::string & name, const AttributeVector::Config & cfg);
    ~SingleValueNumericPostingAttribute();

    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_enumIndices.size(), this->_enumIndices.capacity());
    }
    void onAddDocs(DocId docIdLimit) override {
        forwardedOnAddDoc(docIdLimit, this->_enumIndices.size(), this->_enumIndices.capacity());
    }
    
    void fillPostings(LoadedVector & loaded) override { handleFillPostings(loaded); }
    attribute::IPostingListAttributeBase *getIPostingListAttributeBase() override { return this; }
    const attribute::IPostingListAttributeBase *getIPostingListAttributeBase() const override { return this; }
    void fillPostingsFixupEnum(enumstore::EnumeratedPostingsLoader& loader) override { fillPostingsFixupEnumBase(loader); }
};

} // namespace search

