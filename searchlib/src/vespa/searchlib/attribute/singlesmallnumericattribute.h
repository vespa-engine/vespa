// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "search_context.h"
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <limits>

namespace search {

class GrowStrategy;

class SingleValueSmallNumericAttribute : public IntegerAttributeTemplate<int8_t>
{
private:
    typedef IntegerAttributeTemplate<int8_t> B;
    typedef B::BaseType      T;
    typedef B::DocId         DocId;
    typedef B::EnumHandle    EnumHandle;
    typedef B::largeint_t    largeint_t;
    typedef B::Weighted      Weighted;
    typedef B::WeightedInt   WeightedInt;
    typedef B::WeightedFloat WeightedFloat;
    typedef B::WeightedEnum  WeightedEnum;
    typedef B::generation_t generation_t;

protected:
    typedef uint32_t Word;  // Large enough to contain numDocs.
private:
    Word _valueMask;            // 0x01, 0x03 or 0x0f
    uint32_t _valueShiftShift;  // 0x00, 0x01 or 0x02
    uint32_t _valueShiftMask;   // 0x1f, 0x0f or 0x07
    uint32_t _wordShift;        // 0x05, 0x04 or 0x03

    using DataVector = vespalib::RcuVectorBase<Word>;
    DataVector _wordData;

    T getFromEnum(EnumHandle) const override {
        return T();
    }

protected:
    bool findEnum(T, EnumHandle &) const override {
        return false;
    }

    void set(DocId doc, T v) {
        Word &word_ref = _wordData[doc >> _wordShift];
        uint32_t valueShift = (doc & _valueShiftMask) << _valueShiftShift;
        Word word = vespalib::atomic::load_ref_relaxed(word_ref);
        word = (word & ~(_valueMask << valueShift)) |
               ((v & _valueMask) << valueShift);
        vespalib::atomic::store_ref_relaxed(word_ref, word);
    }


public:

    SingleValueSmallNumericAttribute(const vespalib::string & baseFileName, const Config &c, Word valueMask,
                                     uint32_t valueShiftShift, uint32_t valueShiftMask, uint32_t wordShift);

    ~SingleValueSmallNumericAttribute() override;

    uint32_t getValueCount(DocId doc) const override {
        if (doc >= B::getNumDocs()) {
            return 0;
        }
        return 1;
    }
    void onCommit() override;
    void onAddDocs(DocId docIdLimit) override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    bool addDoc(DocId & doc) override;
    bool onLoad(vespalib::Executor *executor) override;
    void onSave(IAttributeSaveTarget &saveTarget) override;

    std::unique_ptr<attribute::SearchContext>
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    T getFast(DocId doc) const {
        const Word &word = _wordData.acquire_elem_ref(doc >> _wordShift);
        uint32_t valueShift = (doc & _valueShiftMask) << _valueShiftShift;
        return (vespalib::atomic::load_ref_relaxed(word) >> valueShift) & _valueMask;
    }

    //-------------------------------------------------------------------------
    // new read api
    //-------------------------------------------------------------------------
    T get(DocId doc) const override {
        return getFast(doc);
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
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        (void) doc; (void) e; (void) sz;
        return 0;
    }

    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;
    uint64_t getEstimatedSaveByteSize() const override;
};

class SingleValueSemiNibbleNumericAttribute : public SingleValueSmallNumericAttribute
{
public:
    SingleValueSemiNibbleNumericAttribute(const vespalib::string & baseFileName, const GrowStrategy & grow);
};

class SingleValueNibbleNumericAttribute : public SingleValueSmallNumericAttribute
{
public:
    SingleValueNibbleNumericAttribute(const vespalib::string & baseFileName, const GrowStrategy & grow);
};

}

