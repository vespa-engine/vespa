// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SelfType = SingleValueNumericPostingAttribute<B>;
    using ValueModifier = typename B::BaseClass::ValueModifier;
    using generation_t = typename SingleValueNumericEnumAttribute<B>::generation_t;

    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handle_load_posting_lists;
    using PostingParent::handle_load_posting_lists_and_update_enum_store;
    using PostingParent::forwardedOnAddDoc;

    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c, EnumStore & enumStore,
                                std::map<DocId, EnumIndex> & currEnumIndices);
    void makePostingChange(const vespalib::datastore::EntryComparator &cmp,
                           const std::map<DocId, EnumIndex> &currEnumIndices,
                           PostingMap &changePost);

    void applyValueChanges(EnumStoreBatchUpdater& updater) override;

public:
    SingleValueNumericPostingAttribute(const vespalib::string & name, const AttributeVector::Config & cfg);
    ~SingleValueNumericPostingAttribute();

    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_enumIndices.size(), this->_enumIndices.capacity());
    }
    void onAddDocs(DocId docIdLimit) override {
        forwardedOnAddDoc(docIdLimit, this->_enumIndices.size(), this->_enumIndices.capacity());
    }
    
    void load_posting_lists(LoadedVector& loaded) override { handle_load_posting_lists(loaded); }
    attribute::IPostingListAttributeBase *getIPostingListAttributeBase() override { return this; }
    const attribute::IPostingListAttributeBase *getIPostingListAttributeBase() const override { return this; }
    void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader) override {
        handle_load_posting_lists_and_update_enum_store(loader);
    }
};

} // namespace search

