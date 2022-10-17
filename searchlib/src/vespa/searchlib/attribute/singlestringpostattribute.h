// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringattribute.h"
#include "postinglistattribute.h"

namespace search {

/**
 * Implementation of single value string attribute that in addition to enum store
 * uses an underlying posting list to provide faster search.
 *
 * B: EnumAttribute<StringAttribute>
 */
template <typename B>
class SingleValueStringPostingAttributeT
    : public SingleValueStringAttributeT<B>,
      protected PostingListAttributeSubBase<AttributePosting,
                                            typename B::LoadedVector,
                                            typename B::LoadedValueType,
                                            typename B::EnumStore>
{
public:
    using EnumStore = typename SingleValueStringAttributeT<B>::EnumStore;
    using EnumStoreBatchUpdater = typename EnumStore::BatchUpdater;

private:
    using LoadedVector = typename B::LoadedVector;
    using PostingParent = PostingListAttributeSubBase<AttributePosting,
                                                      LoadedVector,
                                                      typename B::LoadedValueType,
                                                      typename B::EnumStore>;

    using Change = StringAttribute::Change;
    using ChangeVector = StringAttribute::ChangeVector;
    using ComparatorType = typename EnumStore::ComparatorType;
    using DocId = typename SingleValueStringAttributeT<B>::DocId;
    using EnumIndex = typename SingleValueStringAttributeT<B>::EnumIndex;
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SelfType = SingleValueStringPostingAttributeT<B>;
    using ValueModifier = typename SingleValueStringAttributeT<B>::ValueModifier;
    using generation_t = typename SingleValueStringAttributeT<B>::generation_t;

    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handle_load_posting_lists;
    using PostingParent::handle_load_posting_lists_and_update_enum_store;
    using PostingParent::forwardedOnAddDoc;
public:
    using PostingList = typename PostingParent::PostingList;
    using Dictionary = EnumPostingTree;
    using PostingParent::getPostingList;

private:
    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c,
                                EnumStore & enumStore,
                                std::map<DocId, EnumIndex> &currEnumIndices);

    void makePostingChange(const vespalib::datastore::EntryComparator &cmp,
                           IEnumStoreDictionary& dictionary,
                           const std::map<DocId, EnumIndex> &currEnumIndices,
                           PostingMap &changePost);

    void applyValueChanges(EnumStoreBatchUpdater& updater) override;
public:
    SingleValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c);
    SingleValueStringPostingAttributeT(const vespalib::string & name);
    ~SingleValueStringPostingAttributeT();

    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_enumIndices.size(), this->_enumIndices.capacity());
    }

    void onAddDocs(DocId lidLimit) override {
        forwardedOnAddDoc(lidLimit, this->_enumIndices.size(), this->_enumIndices.capacity());
    }

    void load_posting_lists(LoadedVector& loaded) override {
        handle_load_posting_lists(loaded);
    }

    attribute::IPostingListAttributeBase * getIPostingListAttributeBase() override {
        return this;
    }

    const attribute::IPostingListAttributeBase * getIPostingListAttributeBase() const override {
        return this;
    }

    void load_posting_lists_and_update_enum_store(enumstore::EnumeratedPostingsLoader& loader) override {
        handle_load_posting_lists_and_update_enum_store(loader);
    }
};

using SingleValueStringPostingAttribute = SingleValueStringPostingAttributeT<EnumAttribute<StringAttribute> >;

} // namespace search
