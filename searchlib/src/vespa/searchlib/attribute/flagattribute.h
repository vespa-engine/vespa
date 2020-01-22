// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multinumericattribute.h"

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
        SearchContext(std::unique_ptr<QueryTermSimple> qTerm, const FlagAttributeT<B> & toBeSearched);

        std::unique_ptr<queryeval::SearchIterator>
        createIterator(fef::TermFieldMatchData * matchData, bool strict) override;

    private:
        bool _zeroHits;

        template <class SC> friend class FlagAttributeIteratorT;
        template <class SC> friend class FlagAttributeIteratorStrict;
    };
    bool onLoad() override;
    bool onLoadEnumerated(ReaderBase &attrReader) override;
    AttributeVector::SearchContext::UP
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;
    void clearOldValues(DocId doc) override;
    void setNewValues(DocId doc, const std::vector<typename B::WType> & values) override;

public:
    void setNewBVValue(DocId doc, typename B::WType::ValueType value);

private:
    bool onAddDoc(DocId doc) override;
    void onAddDocs(DocId docIdLimit) override;
    void ensureGuardBit(BitVector & bv);
    void ensureGuardBit();
    void clearGuardBit(DocId doc);
    void resizeBitVectors(uint32_t neededSize);
    void removeOldGenerations(vespalib::GenerationHandler::generation_t firstUsed) override;
    uint32_t getOffset(int8_t value) const { return value + 128; }
    BitVector * getBitVector(typename B::BaseType value) const {
        return _bitVectors[value + 128];
    }

    vespalib::GenerationHolder               _bitVectorHolder;
    std::vector<std::shared_ptr<BitVector> > _bitVectorStore;
    std::vector<BitVector *>                 _bitVectors;
    uint32_t                                 _bitVectorSize;
    template <class SC> friend class FlagAttributeIteratorT;
    template <class SC> friend class FlagAttributeIteratorStrict;
};

typedef FlagAttributeT<FlagBaseImpl> FlagAttribute;
typedef FlagAttributeT<HugeFlagBaseImpl> HugeFlagAttribute;

} // namespace search

