// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "numeric_direct_posting_store_adapter.h"
#include "multinumericenumattribute.h"
#include "postinglistattribute.h"
#include "i_docid_with_weight_posting_store.h"

namespace search {

/**
 * Implementation of multi value numeric attribute that in addition to enum store and
 * multi value mapping uses an underlying posting list to provide faster search.
 * This class is used for both array and weighted set types.
 *
 * B: EnumAttribute<BaseClass>
 * M: IEnumStore::Index (array) or
 *    multivalue::WeightedValue<IEnumStore::Index> (weighted set)
 * M specifies the type stored in the MultiValueMapping
 */
template <typename B, typename M>
class MultiValueNumericPostingAttribute
    : public MultiValueNumericEnumAttribute<B, M>,
      protected PostingListAttributeSubBase<AttributeWeightPosting,
                                            typename B::LoadedVector,
                                            typename B::LoadedValueType,
                                            typename B::EnumStore>
{
public:
    using EnumStore = typename B::EnumStore;
    using EnumIndex = typename EnumStore::Index;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

private:
    friend class PostingListAttributeTest;
    template <typename, typename, typename>
    friend class attribute::PostingSearchContext; // getEnumStore()

    using SelfType = MultiValueNumericPostingAttribute<B, M>;
    using LoadedVector = typename B::LoadedVector;
    using PostingParent = PostingListAttributeSubBase<AttributeWeightPosting, LoadedVector,
                                                      typename B::LoadedValueType, EnumStore>;

    using ComparatorType = typename EnumStore::ComparatorType;
    using Dictionary = EnumPostingTree;
    using DictionaryConstIterator = typename Dictionary::ConstIterator;
    using DocId = typename B::DocId;
    using DocIndices = typename MultiValueNumericEnumAttribute<B, M>::DocIndices;
    using FrozenDictionary = typename Dictionary::FrozenView;
    using Posting = typename PostingParent::Posting;
    using PostingStore = typename PostingParent::PostingStore;
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using WeightedIndex = typename MultiValueNumericEnumAttribute<B, M>::WeightedIndex;
    using generation_t = typename MultiValueNumericEnumAttribute<B, M>::generation_t;

    using DirectPostingStoreAdapterType = attribute::NumericDirectPostingStoreAdapter<IDocidWithWeightPostingStore,
                                                                                      PostingStore, EnumStore>;
    DirectPostingStoreAdapterType _posting_store_adapter;

    using PostingParent::_posting_store;
    using PostingParent::clearAllPostings;
    using PostingParent::handle_load_posting_lists;
    using PostingParent::handle_load_posting_lists_and_update_enum_store;
    using PostingParent::forwardedOnAddDoc;

    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyValueChanges(const DocIndices& docIndices, EnumStoreBatchUpdater& updater) override;

public:
    MultiValueNumericPostingAttribute(const vespalib::string & name, const AttributeVector::Config & cfg);
    ~MultiValueNumericPostingAttribute();

    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    const IDocidWithWeightPostingStore *as_docid_with_weight_posting_store() const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_mvMapping.getNumKeys(), this->_mvMapping.getCapacityKeys());
    }

    void load_posting_lists(LoadedVector& loaded) override {
        handle_load_posting_lists(loaded);
    }

    attribute::IPostingListAttributeBase *getIPostingListAttributeBase() override {
        return this;
    }

    const attribute::IPostingListAttributeBase *getIPostingListAttributeBase() const override {
        return this;
    }

    void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader) override {
        handle_load_posting_lists_and_update_enum_store(loader);
    }
};


} // namespace search

