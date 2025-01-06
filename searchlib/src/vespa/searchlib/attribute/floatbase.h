// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericbase.h"
#include "loadednumericvalue.h"
#include "changevector.h"
#include <vespa/searchcommon/attribute/multivalue.h>

namespace search {

class FloatingPointAttribute : public NumericAttribute
{
public:
    ~FloatingPointAttribute() override;
    template<typename Accessor>
    bool append(DocId doc, Accessor & ac) {
        return AttributeVector::append(_changes, doc, ac);
    }
    bool append(DocId doc, double v, int32_t weight) {
        return AttributeVector::append(_changes, doc, NumericChangeData<double>(v), weight);
    }
    bool remove(DocId doc, double v, int32_t weight) {
        return AttributeVector::remove(_changes, doc, NumericChangeData<double>(v), weight);
    }
    bool update(DocId doc, double v) {
        return AttributeVector::update(_changes, doc, NumericChangeData<double>(v));
    }
    bool apply(DocId doc, const ArithmeticValueUpdate & op);
    bool applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust) override;
    bool applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust) override;
    uint32_t clearDoc(DocId doc) override;
protected:
    FloatingPointAttribute(const std::string & name, const Config & c);
    using Change = ChangeTemplate<NumericChangeData<double>>;
    using ChangeVector = ChangeVectorT<Change>;
    ChangeVector _changes;

    vespalib::MemoryUsage getChangeVectorMemoryUsage() const override;
private:
    std::span<const char> get_raw(DocId) const override;
    uint32_t get(DocId doc, std::string * v, uint32_t sz) const override;
    uint32_t get(DocId doc, const char ** v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override;
};

template<typename T>
class  FloatingPointAttributeTemplate : public FloatingPointAttribute
{
public:
    using Weighted = WeightedType<T>;
protected:
    using EnumEntryType = T;
    using LoadedNumericValueT = attribute::LoadedNumericValue<T>;

public:
    using BaseType = T;
    using LoadedValueType = T;
    using LoadedVector = SequentialReadModifyWriteInterface<LoadedNumericValueT>;
    virtual T get(DocId doc) const = 0;
    virtual T getFromEnum(EnumHandle e) const = 0;
    T defaultValue() const { return isMutable() ? 0.0 : attribute::getUndefined<T>(); }
    bool isUndefined(DocId doc) const override {
        return attribute::isUndefined(get(doc));
    }
protected:
    explicit FloatingPointAttributeTemplate(const std::string & name);
    FloatingPointAttributeTemplate(const std::string & name, const Config & c);
    ~FloatingPointAttributeTemplate() override;
    virtual bool findEnum(T v, EnumHandle & e) const = 0;
    virtual void load_enum_store(LoadedVector&) {}
    virtual void fillValues(LoadedVector &) {}
    virtual void load_posting_lists(LoadedVector&) {}
    bool is_sortable() const noexcept override;
    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;

    const Change _defaultValue;
private:
    bool findEnum(const char *value, EnumHandle &e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;
};

}
