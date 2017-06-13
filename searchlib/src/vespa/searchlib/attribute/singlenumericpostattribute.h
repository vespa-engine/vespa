// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "singlenumericenumattribute.h"
#include "postinglistattribute.h"
#include "postinglistsearchcontext.h"

namespace search {

/*
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
private:
    friend class PostingListAttributeTest;
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()
    typedef SingleValueNumericPostingAttribute<B> SelfType;
    typedef typename B::LoadedVector    LoadedVector;
    typedef attribute::LoadedEnumAttributeVector LoadedEnumAttributeVector;
    typedef PostingListAttributeSubBase<AttributePosting,
                                        LoadedVector,
                                        typename B::LoadedValueType,
                                        typename B::EnumStore> PostingParent;
public:
    typedef typename SingleValueNumericEnumAttribute<B>::EnumStore     EnumStore;
private:
    typedef typename SingleValueEnumAttributeBase::EnumIndex           EnumIndex;
    typedef typename SingleValueNumericEnumAttribute<B>::generation_t  generation_t;
public:
    typedef typename SingleValueNumericEnumAttribute<B>::T             T;
private:

    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;
    typedef typename SingleValueNumericEnumAttribute<B>::SingleSearchContext SingleSearchContext;
    typedef SingleSearchContext     SingleNumericSearchContext;
    typedef attribute::NumericPostingSearchContext<SingleNumericSearchContext, SelfType, btree::BTreeNoLeafData> SinglePostingSearchContext;

    typedef typename PostingParent::PostingMap            PostingMap;
    typedef typename B::BaseClass::Change                 Change;
    typedef typename B::BaseClass::ChangeVector           ChangeVector;
    typedef typename B::BaseClass::ChangeVector::const_iterator ChangeVectorIterator;
    typedef typename B::BaseClass::DocId                  DocId;
    typedef typename B::BaseClass::ValueModifier          ValueModifier;

public:
    typedef EnumPostingTree Dictionary;
private:
    typedef typename Dictionary::Iterator DictionaryIterator;
    typedef typename Dictionary::ConstIterator DictionaryConstIterator;
    typedef typename EnumStore::ComparatorType ComparatorType;
    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handleFillPostings;
    using PostingParent::fillPostingsFixupEnumBase;
    using PostingParent::forwardedOnAddDoc;

    void freezeEnumDictionary() override;
    void mergeMemoryStats(MemoryUsage & total) override;
    void applyUpdateValueChange(const Change & c, EnumStore & enumStore,
                                std::map<DocId, EnumIndex> & currEnumIndices);
    void makePostingChange(const EnumStoreComparator *cmp,
                           const std::map<DocId, EnumIndex> &currEnumIndices,
                           PostingMap &changePost);

    void applyValueChanges(EnumStoreBase::IndexVector & unused) override;

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
    void fillPostingsFixupEnum(const LoadedEnumAttributeVector &loaded) override { fillPostingsFixupEnumBase(loaded); }
};

} // namespace search

