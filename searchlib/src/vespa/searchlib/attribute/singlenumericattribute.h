// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integerbase.h"
#include "floatbase.h"
#include "search_context.h"
#include <vespa/vespalib/util/atomic.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <limits>

namespace search {

template <typename B>
class SingleValueNumericAttribute final : public B {
private:
    using T = typename B::BaseType;
    using DataVector = vespalib::RcuVectorBase<T>;
    using DocId = typename B::DocId;
    using EnumHandle = typename B::EnumHandle;
    using Weighted = typename B::Weighted;
    using WeightedEnum = typename B::WeightedEnum;
    using WeightedFloat = typename B::WeightedFloat;
    using WeightedInt = typename B::WeightedInt;
    using generation_t = typename B::generation_t;
    using largeint_t = typename B::largeint_t;

    using B::getGenerationHolder;

    DataVector _data;

    T getFromEnum(EnumHandle e) const override {
        (void) e;
        return T();
    }

protected:
    bool findEnum(T value, EnumHandle & e) const override {
        (void) value; (void) e;
        return false;
    }

public:
    explicit SingleValueNumericAttribute(const vespalib::string & baseFileName);  // Only for testing
    SingleValueNumericAttribute(const vespalib::string & baseFileName, const AttributeVector::Config & c);

    ~SingleValueNumericAttribute() override;

    uint32_t getValueCount(DocId doc) const override {
        if (doc >= B::getNumDocs()) {
            return 0;
        }
        return 1;
    }
    void onCommit() override;
    void onAddDocs(DocId lidLimit) override;
    void onUpdateStat() override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void before_inc_generation(generation_t current_gen) override;
    bool addDoc(DocId & doc) override;
    bool onLoad(vespalib::Executor *executor) override;

    bool onLoadEnumerated(ReaderBase &attrReader);

    std::unique_ptr<attribute::SearchContext>
    getSearch(std::unique_ptr<QueryTermSimple> term, const attribute::SearchContextParams & params) const override;

    void set(DocId doc, T v) {
        vespalib::atomic::store_ref_relaxed(_data[doc], v);
    }

    T getFast(DocId doc) const {
        return vespalib::atomic::load_ref_relaxed(_data.acquire_elem_ref(doc));
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
    uint32_t getEnum(DocId doc) const override {
        (void) doc;
        return std::numeric_limits<uint32_t>::max(); // does not have enum
    }
    uint32_t get(DocId doc, largeint_t * v, uint32_t sz) const override {
        (void) sz;
        v[0] = static_cast<largeint_t>(getFast(doc));
        return 1;
    }
    uint32_t get(DocId doc, double * v, uint32_t sz) const override {
        (void) sz;
        v[0] = static_cast<double>(getFast(doc));
        return 1;
    }
    uint32_t get(DocId doc, EnumHandle * e, uint32_t sz) const override {
        (void) sz;
        e[0] = getEnum(doc);
        return 1;
    }
    uint32_t get(DocId doc, WeightedInt * v, uint32_t sz) const override {
        (void) sz;
        v[0] = WeightedInt(static_cast<largeint_t>(getFast(doc)));
        return 1;
    }
    uint32_t get(DocId doc, WeightedFloat * v, uint32_t sz) const override {
        (void) sz;
        v[0] = WeightedFloat(static_cast<double>(getFast(doc)));
        return 1;
    }
    uint32_t get(DocId doc, WeightedEnum * e, uint32_t sz) const override {
        (void) doc; (void) e; (void) sz;
        return 0;
    }

    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;
    void onShrinkLidSpace() override;
    std::unique_ptr<AttributeSaver> onInitSave(vespalib::stringref fileName) override;
};

}

