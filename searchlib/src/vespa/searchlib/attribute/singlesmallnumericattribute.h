// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "floatbase.h"
#include <vespa/searchlib/common/rcuvector.h>
#include <limits>

namespace search {

class SingleValueSmallNumericAttribute : public IntegerAttributeTemplate<int8_t>
{
private:
//    friend class AttributeVector::SearchContext;
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

    typedef search::attribute::RcuVectorBase<Word> DataVector;
    DataVector _wordData;

    T getFromEnum(EnumHandle e) const override {
        (void) e;
        return T();
    }

protected:
    bool findEnum(T value, EnumHandle & e) const override {
        (void) value; (void) e;
        return false;
    }

    void set(DocId doc, T v) {
        Word &word = _wordData[doc >> _wordShift];
        uint32_t valueShift = (doc & _valueShiftMask) << _valueShiftShift;
        word = (word & ~(_valueMask << valueShift)) |
               ((v & _valueMask) << valueShift);
    }


public:
    /*
     * Specialization of SearchContext
     */
    class SingleSearchContext : public NumericAttribute::Range<T>, public SearchContext
    {
    private:
        const Word *_wordData;
        Word _valueMask;
        uint32_t _valueShiftShift;
        uint32_t _valueShiftMask;
        uint32_t _wordShift;

        int32_t onFind(DocId docId, int32_t elementId, int32_t & weight) const override {
            return find(docId, elementId, weight);
        }

        int32_t onFind(DocId docId, int32_t elementId) const override {
            return find(docId, elementId);
        }

        bool valid() const override;

    public:
        SingleSearchContext(std::unique_ptr<QueryTermSimple> qTerm, const NumericAttribute & toBeSearched);

        int32_t find(DocId docId, int32_t elemId, int32_t & weight) const {
            if ( elemId != 0) return -1;
            const Word &word = _wordData[docId >> _wordShift];
            uint32_t valueShift = (docId & _valueShiftMask) << _valueShiftShift;
            T v = (word >> valueShift) & _valueMask;
            weight = 1;
            return match(v) ? 0 : -1;
        }

        int32_t find(DocId docId, int32_t elemId) const {
            if ( elemId != 0) return -1;
            const Word &word = _wordData[docId >> _wordShift];
            uint32_t valueShift = (docId & _valueShiftMask) << _valueShiftShift;
            T v = (word >> valueShift) & _valueMask;
            return match(v) ? 0 : -1;
        }

        Int64Range getAsIntegerTerm() const override;

        std::unique_ptr<queryeval::SearchIterator>
        createFilterIterator(fef::TermFieldMatchData * matchData, bool strict) override;
    };

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
    void removeOldGenerations(generation_t firstUsed) override;
    void onGenerationChange(generation_t generation) override;
    bool addDoc(DocId & doc) override;
    bool onLoad() override;
    void onSave(IAttributeSaveTarget &saveTarget) override;

    SearchContext::UP
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    T getFast(DocId doc) const {
        const Word &word = _wordData[doc >> _wordShift];
        uint32_t valueShift = (doc & _valueShiftMask) << _valueShiftShift;
        return (word >> valueShift) & _valueMask;
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
    void getEnumValue(const EnumHandle * v, uint32_t *e, uint32_t sz) const override {
        (void) v;
        (void) e;
        (void) sz;
    }
    double getFloat(DocId doc) const override {
        return static_cast<double>(getFast(doc));
    }
    uint32_t getEnum(DocId doc) const override {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    uint32_t getAll(DocId doc, T * v, uint32_t sz) const override {
        if (sz > 0) {
            v[0] = getFast(doc);
        }
        return 1;
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
    uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const override {
        (void) doc; (void) v; (void) sz;
        return 0;
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

    void clearDocs(DocId lidLow, DocId lidLimit) override;
    void onShrinkLidSpace() override;
    uint64_t getEstimatedSaveByteSize() const override;
};


class SingleValueBitNumericAttribute : public SingleValueSmallNumericAttribute
{
public:
    SingleValueBitNumericAttribute(const vespalib::string & baseFileName, const search::GrowStrategy & grow);
};


class SingleValueSemiNibbleNumericAttribute : public SingleValueSmallNumericAttribute
{
public:
    SingleValueSemiNibbleNumericAttribute(const vespalib::string & baseFileName, const search::GrowStrategy & grow);
};

class SingleValueNibbleNumericAttribute : public SingleValueSmallNumericAttribute
{
public:
    SingleValueNibbleNumericAttribute(const vespalib::string & baseFileName, const search::GrowStrategy & grow);
};

}

