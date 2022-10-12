// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multinumericattribute.h"
#include "multi_numeric_search_context.h"
#include <vespa/searchlib/common/growablebitvector.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

namespace search {

typedef MultiValueNumericAttribute< IntegerAttributeTemplate<int8_t>, int8_t > FlagBaseImpl;

template <typename B>
class FlagAttributeT : public B {
public:
    FlagAttributeT(const vespalib::string & baseFileName, const AttributeVector::Config & cfg);
private:
    typedef AttributeVector::DocId DocId;
    bool onLoad(vespalib::Executor *executor) override;
    bool onLoadEnumerated(ReaderBase &attrReader) override;
    std::unique_ptr<attribute::SearchContext>
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;
    void clearOldValues(DocId doc) override;
    void setNewValues(DocId doc, const std::vector<typename B::WType> & values) override;

public:
    void setNewBVValue(DocId doc, multivalue::ValueType_t<typename B::WType> value);

private:
    bool onAddDoc(DocId doc) override;
    void onAddDocs(DocId docIdLimit) override;
    void ensureGuardBit(BitVector & bv);
    void ensureGuardBit();
    void clearGuardBit(DocId doc);
    void resizeBitVectors(uint32_t neededSize);
    void reclaim_memory(vespalib::GenerationHandler::generation_t oldest_used_gen) override;
    uint32_t getOffset(int8_t value) const { return value + 128; }

    using AtomicBitVectorPtr = vespalib::datastore::AtomicValueWrapper<BitVector *>;
    vespalib::GenerationHolder                       _bitVectorHolder;
    std::vector<std::shared_ptr<GrowableBitVector> > _bitVectorStore;
    std::vector<AtomicBitVectorPtr>                  _bitVectors;
    uint32_t                                         _bitVectorSize;
};

typedef FlagAttributeT<FlagBaseImpl> FlagAttribute;

} // namespace search

