// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/multinumericattribute.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/common/rcuvector.h>

namespace search {

typedef MultiValueNumericAttribute< IntegerAttributeTemplate<int8_t>, multivalue::Value<int8_t> > FlagBaseImpl;
typedef MultiValueNumericAttribute< IntegerAttributeTemplate<int8_t>, multivalue::Value<int8_t> > HugeFlagBaseImpl;

template <typename B>
class FlagAttributeT : public B {
public:
    FlagAttributeT(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
private:
    typedef AttributeVector::DocId DocId;
    typedef FlagBaseImpl::ArraySearchContext BaseSC;
    class SearchContext : public BaseSC {
    public:
        typedef FlagAttributeT<B> Attribute;
        SearchContext(QueryTermSimple::UP qTerm, const FlagAttributeT<B> & toBeSearched);

        virtual std::unique_ptr<queryeval::SearchIterator>
        createIterator(fef::TermFieldMatchData * matchData,
                       bool strict);

    private:
        bool _zeroHits;

        template <class SC> friend class FlagAttributeIteratorT;
        template <class SC> friend class FlagAttributeIteratorStrict;
    };
    virtual bool onLoad();

    virtual bool
    onLoadEnumerated(typename B::ReaderBase &attrReader);

    AttributeVector::SearchContext::UP
    getSearch(QueryTermSimple::UP term, const AttributeVector::SearchContext::Params & params) const override;
    virtual void clearOldValues(DocId doc);
    virtual void setNewValues(DocId doc, const std::vector<typename B::WType> & values);

public:
    void
    setNewBVValue(DocId doc, typename B::WType::ValueType value);

private:
    virtual bool onAddDoc(DocId doc);
    void ensureGuardBit(BitVector & bv);
    void ensureGuardBit();
    void clearGuardBit(DocId doc);
    void resizeBitVectors(uint32_t neededSize);
    void removeOldGenerations(vespalib::GenerationHandler::generation_t firstUsed);
    uint32_t getOffset(int8_t value) const { return value + 128; }
    BitVector * getBitVector(typename B::BaseType value) const {
        return _bitVectors[value + 128];
    }

    vespalib::GenerationHolder                   _bitVectorHolder;
    std::vector<std::shared_ptr<BitVector> > _bitVectorStore;
    std::vector<BitVector *>                 _bitVectors;
    uint32_t                                     _bitVectorSize;
    template <class SC> friend class FlagAttributeIteratorT;
    template <class SC> friend class FlagAttributeIteratorStrict;
};

typedef FlagAttributeT<FlagBaseImpl> FlagAttribute;
typedef FlagAttributeT<HugeFlagBaseImpl> HugeFlagAttribute;

} // namespace search

