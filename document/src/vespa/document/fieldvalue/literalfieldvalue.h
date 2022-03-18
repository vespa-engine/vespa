// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::LiteralFieldValue
 * \ingroup fieldvalue
 *
 * \brief Super class for primitive field values not containing numbers.
 *
 * Templated superclass to minimalize need for code duplication in such types.
 * This covers strings, raw, termboost, URI at time of writing.
 *
 * Note that raw is a bit different than the rest as it define addZeroTerm
 * false. Otherwise, the types are functionally equivalent, only difference is
 * the type id.
 */
#pragma once

#include "fieldvalue.h"
#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/vespalib/stllike/hash_fun.h>

namespace document {

class LiteralFieldValueB : public FieldValue {
public:
    typedef vespalib::string string;
    typedef vespalib::stringref stringref;
    typedef std::unique_ptr<LiteralFieldValueB> UP;
    typedef string value_type;

    explicit LiteralFieldValueB(Type type);
    ~LiteralFieldValueB();

    LiteralFieldValueB(const LiteralFieldValueB &);
    LiteralFieldValueB(Type type, const stringref & value);

    const value_type & getValue() const { sync(); return _backing; }
    /**
     * Get a ref to the value. If value has recently been deserialized, and
     * never needed as an std::string before, this method lets you get a hold
     * of the data without creating the string.
     */
    stringref getValueRef() const { return _value; }

    LiteralFieldValueB & operator=(const LiteralFieldValueB &);

    void setValueRef(stringref value) {
        _value = value;
    }

    void setValue(stringref value) {
        _backing = value;
        _value = _backing;
    }
    size_t hash() const override final { return vespalib::hashValue(_value.data(), _value.size()); }
    void setValue(const char* val, size_t size) { setValue(stringref(val, size)); }

    int compare(const FieldValue& other) const override;
    int fastCompare(const FieldValue& other) const override final;

    vespalib::string getAsString() const override;
    std::pair<const char*, size_t> getAsRaw() const override;

    void printXml(XmlOutputStream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    FieldValue& assign(const FieldValue&) override;

    FieldValue& operator=(vespalib::stringref) override;
protected:
    void syncBacking() const __attribute__((noinline));
    void sync() const {
        if (__builtin_expect(_backing.data() != _value.data(), false)) {
            syncBacking();
        }
    }
    mutable stringref _value;
    mutable string    _backing; // Lazily set when needed
};

template<typename SubClass, int dataType>
class LiteralFieldValue : public LiteralFieldValueB {
public:
    explicit LiteralFieldValue(Type type) : LiteralFieldValueB(type) { }
    LiteralFieldValue(Type type, const stringref& value) : LiteralFieldValueB(type, value) { }
    const DataType *getDataType() const override;
};

extern template class LiteralFieldValue<RawFieldValue, DataType::T_RAW>;
extern template class LiteralFieldValue<StringFieldValue, DataType::T_STRING>;

} // document

