// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/numericbase.h>
#include <vespa/searchlib/attribute/multivalue.h>
#include <vespa/searchlib/attribute/loadednumericvalue.h>
#include <vespa/searchlib/attribute/changevector.h>

namespace search {

// forward declaration of class in enumstore.h
template <typename T>
class NumericEntryType;

class IntegerAttribute : public NumericAttribute
{
public:
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
    virtual bool applyWeight(DocId doc, const FieldValue & fv, const ArithmeticValueUpdate & wAdjust);
    virtual uint32_t clearDoc(DocId doc);
protected:
    IntegerAttribute(const vespalib::string & name, const Config & c);
    typedef ChangeTemplate<NumericChangeData<largeint_t> > Change;
    typedef ChangeVectorT< Change > ChangeVector;
    ChangeVector _changes;

private:
    virtual const char * getString(DocId doc, char * s, size_t sz) const { largeint_t v = getInt(doc); snprintf(s, sz, "%" PRId64, v); return s; }
    virtual uint32_t get(DocId doc, vespalib::string * v, uint32_t sz) const;
    virtual uint32_t get(DocId doc, const char ** v, uint32_t sz) const;
    virtual uint32_t get(DocId doc, WeightedString * v, uint32_t sz) const;
    virtual uint32_t get(DocId doc, WeightedConstChar * v, uint32_t sz) const;
    virtual largeint_t getIntFromEnum(EnumHandle e) const = 0;
};

template<typename T>
class IntegerAttributeTemplate : public IntegerAttribute
{
public:
    typedef WeightedType<T> Weighted;
    virtual uint32_t getAll(DocId doc, T * v, uint32_t sz) const = 0;
    virtual uint32_t getAll(DocId doc, Weighted * v, uint32_t sz) const = 0;
protected:
    typedef NumericEntryType<T>          EnumEntryType;
    typedef attribute::LoadedNumericValue<T> LoadedNumericValueT;
public:
    typedef T BaseType;
    typedef T LoadedValueType;
    typedef SequentialReadModifyWriteInterface<LoadedNumericValueT> LoadedVector;
    virtual uint32_t getRawValues(DocId doc, const multivalue::Value<T> * & values) const {
        (void) doc;
        (void) values;
        throw std::runtime_error(getNativeClassName() + "::getRawValues() not implemented.");
    }

protected:
    IntegerAttributeTemplate(const vespalib::string & name) :
        IntegerAttribute(name, BasicType::fromType(T())),
        _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
    { }
    IntegerAttributeTemplate(const vespalib::string & name, const Config & c) :
        IntegerAttribute(name, c),
        _defaultValue(ChangeBase::UPDATE, 0, defaultValue())
    { assert(c.basicType() == BasicType::fromType(T())); }
    IntegerAttributeTemplate(const vespalib::string & name,
                             const Config & c,
                             const BasicType &realType)
        :  IntegerAttribute(name, c),
           _defaultValue(ChangeBase::UPDATE, 0, 0u)
    {
        assert(c.basicType() == realType);
        (void) realType;
        assert(BasicType::fromType(T()) == BasicType::INT8);
    }
    static T defaultValue() { return attribute::getUndefined<T>(); }
    virtual bool findEnum(T v, EnumHandle & e) const = 0;
    virtual largeint_t getDefaultValue() const { return defaultValue(); }
    virtual bool isUndefined(DocId doc) const { return get(doc) == defaultValue(); }
    Change _defaultValue;
private:
    virtual bool findEnum(const char *value, EnumHandle &e) const {
        vespalib::asciistream iss(value);
        int64_t ivalue = 0;
        try {
            iss >> ivalue;
        } catch (const vespalib::IllegalArgumentException &) {
        }
        return findEnum(ivalue, e);
    }
    virtual T get(DocId doc) const = 0;
    virtual T getFromEnum(EnumHandle e) const = 0;
    virtual largeint_t getIntFromEnum(EnumHandle e) const {
        T v(getFromEnum(e));
        if (attribute::isUndefined<T>(v)) {
            return attribute::getUndefined<largeint_t>();
        }
        return v;
    }
    virtual long onSerializeForAscendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const {
        (void) bc;
        if (available >= long(sizeof(T))) {
            T origValue(get(doc));
            vespalib::serializeForSort< vespalib::convertForSort<T, true> >(origValue, serTo);
        } else {
            return -1;
        }
        return sizeof(T);
    }
    virtual long onSerializeForDescendingSort(DocId doc, void * serTo, long available, const common::BlobConverter * bc) const {
        (void) bc;
        if (available >= long(sizeof(T))) {
            T origValue(get(doc));
            vespalib::serializeForSort< vespalib::convertForSort<T, false> >(origValue, serTo);
        } else {
            return -1;
        }
        return sizeof(T);
    }
};

}

