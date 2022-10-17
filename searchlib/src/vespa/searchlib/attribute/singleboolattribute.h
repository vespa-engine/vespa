// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include <vespa/searchlib/common/growablebitvector.h>
#include <limits>

namespace search {

class GrowStrategy;

/**
 * Attributevector for boolean field values occupying a bit per document
 * and backed by a growable rcu bit vector.
 */
class SingleBoolAttribute final : public IntegerAttributeTemplate<int8_t>
{
public:
    SingleBoolAttribute(const vespalib::string & baseFileName, const GrowStrategy & grow, bool paged);
    ~SingleBoolAttribute() override;

    void onCommit() override;
    bool addDoc(DocId & doc) override;
    void onAddDocs(DocId docIdLimit) override;
    void onUpdateStat() override;
    bool onLoad(vespalib::Executor *executor) override;
    void onSave(IAttributeSaveTarget &saveTarget) override;
    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    uint64_t getEstimatedSaveByteSize() const override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    uint32_t getValueCount(DocId doc) const override {
        return (doc >= _bv.reader().size()) ? 0 : 1;
    }
    largeint_t getInt(DocId doc) const override {
        return static_cast<largeint_t>(getFast(doc));
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(getFast(doc));
    }
    uint32_t getEnum(DocId) const override {
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = static_cast<largeint_t>(getFast(doc));
        }
        return 1;
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = static_cast<double>(getFast(doc));
        }
        return 1;
    }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        if (sz > 0) {
            e[0] = getEnum(doc);
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = WeightedInt(static_cast<largeint_t>(getFast(doc)));
        }
        return 1;
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = WeightedFloat(static_cast<double>(getFast(doc)));
        }
        return 1;
    }
    uint32_t get(DocId, WeightedEnum *, uint32_t) const override {
        return 0;
    }
    int8_t get(DocId doc) const override {
        return getFast(doc);
    }
    const BitVector & getBitVector() const { return _bv.reader(); }
    void setBit(DocId doc, bool value) {
        if (value) {
            _bv.writer().setBitAndMaintainCount(doc);
        } else {
            _bv.writer().clearBitAndMaintainCount(doc);
        }
    }
protected:
    bool findEnum(int8_t, EnumHandle &) const override {
        return false;
    }
private:
    void ensureRoom(DocId docIdLimit);
    int8_t getFromEnum(EnumHandle) const override {
        return 0;
    }
    int8_t getFast(DocId doc) const {
        return _bv.reader().testBit(doc) ? 1 : 0;
    }
    vespalib::alloc::Alloc _init_alloc;
    GrowableBitVector _bv;
};

}
