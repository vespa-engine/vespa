// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/postinglistattribute.h>

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
    virtual void freezeEnumDictionary();
    virtual void mergeMemoryStats(MemoryUsage & total);
    void applyUpdateValueChange(const Change & c,
                                EnumStore & enumStore,
                                std::map<DocId, EnumIndex> &currEnumIndices);

    void
    makePostingChange(const EnumStoreComparator *cmp,
                      Dictionary &dict,
                      const std::map<DocId, EnumIndex> &currEnumIndices,
                      PostingMap &changePost);

    virtual void applyValueChanges(EnumStoreBase::IndexVector & unused);
public:
    SingleValueStringPostingAttributeT(const vespalib::string & name, const AttributeVector::Config & c =
                                       AttributeVector::Config(AttributeVector::BasicType::STRING));
    ~SingleValueStringPostingAttributeT();

    virtual void removeOldGenerations(generation_t firstUsed);
    virtual void onGenerationChange(generation_t generation);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimple::UP term, const AttributeVector::SearchContext::Params & params) const override;

    virtual bool
    onAddDoc(DocId doc)
    {
        return forwardedOnAddDoc(doc,
                                 this->_enumIndices.size(),
                                 this->_enumIndices.capacity());
    }
    
    virtual void
    fillPostings(LoadedVector & loaded)
    {
        handleFillPostings(loaded);
    }

    virtual attribute::IPostingListAttributeBase *
    getIPostingListAttributeBase(void)
    {
        return this;
    }

    virtual void
    fillPostingsFixupEnum(const LoadedEnumAttributeVector &loaded)
    {
        fillPostingsFixupEnumBase(loaded);
    }
};

typedef SingleValueStringPostingAttributeT<EnumAttribute<StringAttribute> > SingleValueStringPostingAttribute;


} // namespace search

