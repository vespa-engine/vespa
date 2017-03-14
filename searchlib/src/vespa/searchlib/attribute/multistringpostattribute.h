// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/multistringattribute.h>
#include <vespa/searchlib/attribute/postinglistattribute.h>
#include "i_document_weight_attribute.h"

namespace search {

/*
 * Implementation of multi value string attribute that in addition to enum store and
 * multi value mapping uses an underlying posting list to provide faster search.
 * This class is used for both array and weighted set types.
 *
 * B: EnumAttribute<StringAttribute>
 * T: multivalue::Value<EnumStoreBase::Index> (array) or
 *    multivalue::WeightedValue<EnumStoreBase::Index> (weighted set)
 */
template <typename B, typename T>
class MultiValueStringPostingAttributeT
    : public MultiValueStringAttributeT<B, T>,
      protected PostingListAttributeSubBase<AttributeWeightPosting,
                                            typename B::LoadedVector,
                                            typename B::LoadedValueType,
                                            typename B::EnumStore>
{
private:
    struct DocumentWeightAttributeAdapter : IDocumentWeightAttribute
    {
        const MultiValueStringPostingAttributeT &self;
        DocumentWeightAttributeAdapter(const MultiValueStringPostingAttributeT &self_in) : self(self_in) {}
        virtual LookupResult lookup(const vespalib::string &term) const override final;
        virtual void create(datastore::EntryRef idx, std::vector<DocumentWeightIterator> &dst) const override final;
        virtual DocumentWeightIterator create(datastore::EntryRef idx) const override final;
    };
    DocumentWeightAttributeAdapter _document_weight_attribute_adapter;

    friend class PostingListAttributeTest;
    template <typename, typename, typename> 
    friend class attribute::PostingSearchContext; // getEnumStore()
    friend class StringAttributeTest;
    typedef MultiValueStringPostingAttributeT<B, T> SelfType;
    typedef typename B::LoadedVector    LoadedVector;
    typedef attribute::LoadedEnumAttributeVector LoadedEnumAttributeVector;
    typedef PostingListAttributeSubBase<AttributeWeightPosting,
                                        LoadedVector,
                                        typename B::LoadedValueType,
                                        typename B::EnumStore> PostingParent;
    typedef typename MultiValueStringAttributeT<B, T>::DocId DocId;
public:
    typedef typename MultiValueStringAttributeT<B, T>::EnumStore EnumStore;
private:
    typedef typename MultiValueStringAttributeT<B, T>::WeightedIndex WeightedIndex;
    typedef typename MultiValueStringAttributeT<B, T>::DocIndices DocIndices;
    typedef typename MultiValueStringAttributeT<B, T>::generation_t generation_t;
    typedef typename PostingParent::PostingList PostingList;
    typedef typename PostingParent::PostingMap  PostingMap;
    typedef typename PostingParent::Posting     Posting;
    using QueryTermSimpleUP = AttributeVector::QueryTermSimpleUP;

    typedef typename MultiValueStringAttributeT<B, T>::StringSetImplSearchContext StringSetImplSearchContext;
    typedef typename MultiValueStringAttributeT<B, T>::StringArrayImplSearchContext StringArrayImplSearchContext;
    typedef attribute::StringPostingSearchContext<StringSetImplSearchContext, SelfType, int32_t> StringSetPostingSearchContext;
    typedef attribute::StringPostingSearchContext<StringArrayImplSearchContext, SelfType, int32_t> StringArrayPostingSearchContext;

    typedef EnumPostingTree Dictionary;
    typedef typename EnumStore::Index EnumIndex;
    typedef typename EnumStore::ComparatorType ComparatorType;
    typedef typename EnumStore::FoldedComparatorType FoldedComparatorType;
    typedef typename Dictionary::Iterator DictionaryIterator;
    typedef typename Dictionary::ConstIterator DictionaryConstIterator;
    typedef typename Dictionary::FrozenView FrozenDictionary;
    using PostingParent::_postingList;
    using PostingParent::clearAllPostings;
    using PostingParent::handleFillPostings;
    using PostingParent::fillPostingsFixupEnumBase;
    using PostingParent::forwardedOnAddDoc;

    virtual void freezeEnumDictionary();
    virtual void mergeMemoryStats(MemoryUsage & total);
    virtual void applyValueChanges(const DocIndices & docIndices, EnumStoreBase::IndexVector & unused);

public:
    MultiValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c =
                                      AttributeVector::Config(AttributeVector::BasicType::STRING,
                                                              attribute::CollectionType::ARRAY));
    ~MultiValueStringPostingAttributeT();

    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const override;

    const IDocumentWeightAttribute *asDocumentWeightAttribute() const override;

    bool onAddDoc(DocId doc) override {
        return forwardedOnAddDoc(doc, this->_mvMapping.getNumKeys(), this->_mvMapping.getCapacityKeys());
    }
    
    void fillPostings(LoadedVector & loaded) override {
        handleFillPostings(loaded);
    }

    attribute::IPostingListAttributeBase * getIPostingListAttributeBase(void) override {
        return this;
    }

    void fillPostingsFixupEnum(const LoadedEnumAttributeVector &loaded) override {
        fillPostingsFixupEnumBase(loaded);
    }
};

typedef MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, multivalue::Value<EnumStoreBase::Index> > ArrayStringPostingAttribute;
typedef MultiValueStringPostingAttributeT<EnumAttribute<StringAttribute>, multivalue::WeightedValue<EnumStoreBase::Index> > WeightedSetStringPostingAttribute;

} // namespace search

