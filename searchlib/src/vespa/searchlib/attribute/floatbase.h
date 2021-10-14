// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericbase.h"
#include "multivalue.h"
#include "loadednumericvalue.h"
#include "changevector.h"

namespace search {

class FloatingPointAttribute : public NumericAttribute
{
public:
    DECLARE_IDENTIFIABLE_ABSTRACT(FloatingPointAttribute);
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
    const char * getString(DocId doc, char * s, size_t sz) const override;
    FloatingPointAttribute(const vespalib::string & name, const Config & c);
    using Change = ChangeTemplate<NumericChangeData<double>>;
    using ChangeVector = ChangeVectorT<Change>;
    ChangeVector _changes;

    virtual vespalib::MemoryUsage getChangeVectorMemoryUsage() const override;
private:
    uint32_t get(DocId doc, vespalib::string * v, uint32_t sz) const override;
    uint32_t get(DocId doc, const char ** v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override;
    virtual double getFloatFromEnum(EnumHandle e) const = 0;
};

template<typename T>
class  FloatingPointAttributeTemplate : public FloatingPointAttribute
{
public:
    using Weighted = WeightedType<T>;
    virtual uint32_t getAll(DocId doc, T * v, uint32_t sz) const = 0;
    virtual uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const = 0;
protected:
    using EnumEntryType = T;
    using LoadedNumericValueT = attribute::LoadedNumericValue<T>;

public:    
    using BaseType = T;
    using LoadedValueType = T;
    using LoadedVector = SequentialReadModifyWriteInterface<LoadedNumericValueT>;
    virtual uint32_t getRawValues(DocId doc, const multivalue::Value<T> * & values) const;
    virtual uint32_t getRawValues(DocId doc, const multivalue::WeightedValue<T> * & values) const;
    virtual T get(DocId doc) const = 0;
    virtual T getFromEnum(EnumHandle e) const = 0;
protected:
    FloatingPointAttributeTemplate(const vespalib::string & name);
    FloatingPointAttributeTemplate(const vespalib::string & name, const Config & c);
    ~FloatingPointAttributeTemplate();
    static T defaultValue() { return attribute::getUndefined<T>(); }
    virtual bool findEnum(T v, EnumHandle & e) const = 0;
    virtual void load_enum_store(LoadedVector&) {}
    virtual void fillValues(LoadedVector &) {}
    virtual void load_posting_lists(LoadedVector&) {}

    largeint_t getDefaultValue() const override { return static_cast<largeint_t>(-std::numeric_limits<T>::max()); }
    Change _defaultValue;
private:
    bool findEnum(const char *value, EnumHandle &e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;
    bool isUndefined(DocId doc) const override;

    double getFloatFromEnum(EnumHandle e) const override;
    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
};

}
