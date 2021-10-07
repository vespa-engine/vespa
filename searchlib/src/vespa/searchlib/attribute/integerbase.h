// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "numericbase.h"
#include "multivalue.h"
#include "loadednumericvalue.h"
#include "changevector.h"

namespace search {

class IntegerAttribute : public NumericAttribute
{
public:
    ~IntegerAttribute();
    DECLARE_IDENTIFIABLE_ABSTRACT(IntegerAttribute);
    bool update(DocId doc, largeint_t v) {
        return AttributeVector::update(_changes, doc, NumericChangeData<largeint_t>(v));
    }
    template<typename Accessor>
    bool append(DocId doc, Accessor & ac) {
        return AttributeVector::append(_changes, doc, ac);
    }
    bool append(DocId doc, largeint_t v, int32_t weight) {
        return AttributeVector::append(_changes, doc, NumericChangeData<largeint_t>(v), weight);
    }
    bool remove(DocId doc, largeint_t v, int32_t weight) {
        return AttributeVector::remove(_changes, doc, NumericChangeData<largeint_t>(v), weight);
    }
    bool apply(DocId doc, const ArithmeticValueUpdate & op);
    bool applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust) override;
    bool applyWeight(DocId doc, const FieldValue& fv, const document::AssignValueUpdate& wAdjust) override;
    uint32_t clearDoc(DocId doc) override;
protected:
    IntegerAttribute(const vespalib::string & name, const Config & c);
    using Change = ChangeTemplate<NumericChangeData<largeint_t>>;
    using ChangeVector = ChangeVectorT<Change>;
    ChangeVector _changes;

    vespalib::MemoryUsage getChangeVectorMemoryUsage() const override;
private:
    const char * getString(DocId doc, char * s, size_t sz) const override;
    uint32_t get(DocId doc, vespalib::string * v, uint32_t sz) const override;
    uint32_t get(DocId doc, const char ** v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const override;
    uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const override;
    virtual largeint_t getIntFromEnum(EnumHandle e) const = 0;
};

template<typename T>
class IntegerAttributeTemplate : public IntegerAttribute
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
    IntegerAttributeTemplate(const vespalib::string & name) :
        IntegerAttribute(name, BasicType::fromType(T())),
        _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
    { }
    IntegerAttributeTemplate(const vespalib::string & name, const Config & c) :
        IntegerAttribute(name, c),
        _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
    {
        assert(c.basicType() == BasicType::fromType(T()));
    }
    IntegerAttributeTemplate(const vespalib::string & name, const Config & c, const BasicType &realType)
        :  IntegerAttribute(name, c),
           _defaultValue(ChangeBase::UPDATE, 0, 0u)
    {
        assert(c.basicType() == realType);
        (void) realType;
        assert(BasicType::fromType(T()) == BasicType::INT8);
    }
    static T defaultValue() { return attribute::getUndefined<T>(); }
    virtual bool findEnum(T v, EnumHandle & e) const = 0;
    virtual void load_enum_store(LoadedVector&) {}
    virtual void fillValues(LoadedVector &) {}
    virtual void load_posting_lists(LoadedVector&) {}

    largeint_t getDefaultValue() const override { return defaultValue(); }
    bool isUndefined(DocId doc) const override { return get(doc) == defaultValue(); }
    Change _defaultValue;
private:
    bool findEnum(const char *value, EnumHandle &e) const override;
    std::vector<EnumHandle> findFoldedEnums(const char *value) const override;

    largeint_t getIntFromEnum(EnumHandle e) const override;
    long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
    long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const override;
};

}
