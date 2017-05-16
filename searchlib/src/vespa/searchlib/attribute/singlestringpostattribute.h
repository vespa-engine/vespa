// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlestringattribute.h"
#include "postinglistattribute.h"

namespace search {

/*
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
private:
    friend class PostingListAttributeTest;
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()
    friend class StringAttributeTest;
    typedef SingleValueStringPostingAttributeT<B> SelfType;
    typedef typename B::LoadedVector    LoadedVector;
    typedef attribute::LoadedEnumAttributeVector LoadedEnumAttributeVector;
    typedef PostingListAttributeSubBase<AttributePosting,
                                        LoadedVector,
                                        typename B::LoadedValueType,
                                        typename B::EnumStore> PostingParent;
    typedef typename SingleValueStringAttributeT<B>::DocId         DocId;
public:
    typedef typename SingleValueStringAttributeT<B>::EnumStore     EnumStore;
private:
    typedef typename SingleValueStringAttributeT<B>::EnumIndex     EnumIndex;
    typedef typename SingleValueStringAttributeT<B>::generation_t  generation_t;
    typedef typename SingleValueStringAttributeT<B>::ValueModifier ValueModifier;

    typedef typename SingleValueStringAttributeT<B>::StringSingleImplSearchContext StringSingleImplSearchContext;
    typedef attribute::StringPostingSearchContext<StringSingleImplSearchContext,
                                                  SelfType,
                                                  btree::BTreeNoLeafData>
    StringSinglePostingSearchContext;

    typedef StringAttribute::Change       Change;
    typedef StringAttribute::ChangeVector ChangeVector;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

    typedef typename PostingParent::PostingList PostingList;
    typedef typename PostingParent::PostingMap  PostingMap;
    // typedef typename PostingParent::Posting     Posting;

    typedef EnumPostingTree Dictionary;
    typedef typename EnumStore::ComparatorType ComparatorType;
    typedef typename EnumStore::FoldedComparatorType FoldedComparatorType;
    typedef typename Dictionary::Iterator   DictionaryIterator;
    typedef typename Dictionary::ConstIterator   DictionaryConstIterator;
    typedef typename Dictionary::FrozenView FrozenDictionary;
    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handleFillPostings;
    using PostingParent::fillPostingsFixupEnumBase;
    using PostingParent::forwardedOnAddDoc;
public:
    using PostingParent::getPostingList;

private:
    void freezeEnumDictionary() override;
    void mergeMemoryStats(MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c,
                                EnumStore & enumStore,
                                std::map<DocId, EnumIndex> &currEnumIndices);

    void
    makePostingChange(const EnumStoreComparator *cmp,
                      Dictionary &dict,
                      const std::map<DocId, EnumIndex> &currEnumIndices,
                      PostingMap &changePost);

    void applyValueChanges(EnumStoreBase::IndexVector & unused) override;
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

    void fillPostingsFixupEnum(const LoadedEnumAttributeVector &loaded) override {
        fillPostingsFixupEnumBase(loaded);
    }
};

typedef SingleValueStringPostingAttributeT<EnumAttribute<StringAttribute> > SingleValueStringPostingAttribute;

} // namespace search
