// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    friend class PostingListAttributeTest;
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()
    friend class StringAttributeTest;

    using LoadedVector = typename B::LoadedVector;
    using PostingParent = PostingListAttributeSubBase<AttributePosting,
                                                      LoadedVector,
                                                      typename B::LoadedValueType,
                                                      typename B::EnumStore>;

    using Change = StringAttribute::Change;
    using ChangeVector = StringAttribute::ChangeVector;
    using ComparatorType = typename EnumStore::ComparatorType;
    using Dictionary = EnumPostingTree;
    using DocId = typename SingleValueStringAttributeT<B>::DocId;
    using EnumIndex = typename SingleValueStringAttributeT<B>::EnumIndex;
    using FoldedComparatorType = typename EnumStore::FoldedComparatorType;
    using LoadedEnumAttributeVector = attribute::LoadedEnumAttributeVector;
    using PostingList = typename PostingParent::PostingList;
    using PostingMap = typename PostingParent::PostingMap;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    using SelfType = SingleValueStringPostingAttributeT<B>;
    using StringSingleImplSearchContext = typename SingleValueStringAttributeT<B>::StringSingleImplSearchContext;
    using StringSinglePostingSearchContext = attribute::StringPostingSearchContext<StringSingleImplSearchContext,
                                                                                   SelfType,
                                                                                   btree::BTreeNoLeafData>;
    using ValueModifier = typename SingleValueStringAttributeT<B>::ValueModifier;
    using generation_t = typename SingleValueStringAttributeT<B>::generation_t;

    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handleFillPostings;
    using PostingParent::fillPostingsFixupEnumBase;
    using PostingParent::forwardedOnAddDoc;
public:
    using PostingParent::getPostingList;

private:
    void freezeEnumDictionary() override;
    void mergeMemoryStats(vespalib::MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c,
                                EnumStore & enumStore,
                                std::map<DocId, EnumIndex> &currEnumIndices);

    void
    makePostingChange(const EnumStoreComparator *cmp,
                      Dictionary &dict,
                      const std::map<DocId, EnumIndex> &currEnumIndices,
                      PostingMap &changePost);

    void applyValueChanges(EnumStoreBatchUpdater& updater) override;
public:
    SingleValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c =
                                       AttributeVector::Config(AttributeVector::BasicType::STRING));
    ~SingleValueStringPostingAttributeT();

    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_enumIndices.size(), this->_enumIndices.capacity());
    }

    void onAddDocs(DocId lidLimit) override {
        forwardedOnAddDoc(lidLimit, this->_enumIndices.size(), this->_enumIndices.capacity());
    }

    void fillPostings(LoadedVector & loaded) override {
        handleFillPostings(loaded);
    }

    attribute::IPostingListAttributeBase * getIPostingListAttributeBase() override {
        return this;
    }

    const attribute::IPostingListAttributeBase * getIPostingListAttributeBase() const override {
        return this;
    }

    void fillPostingsFixupEnum(const LoadedEnumAttributeVector &loaded) override {
        fillPostingsFixupEnumBase(loaded);
    }
};

using SingleValueStringPostingAttribute = SingleValueStringPostingAttributeT<EnumAttribute<StringAttribute> >;

} // namespace search
