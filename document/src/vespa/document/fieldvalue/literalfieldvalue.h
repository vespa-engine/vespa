// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/document/datatype/primitivedatatype.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/vespalib/stllike/hash_fun.h>

namespace document {

class LiteralFieldValueB : public FieldValue {
public:
    typedef vespalib::string string;
    typedef vespalib::stringref stringref;
    DECLARE_IDENTIFIABLE_ABSTRACT(LiteralFieldValueB);
    typedef std::unique_ptr<LiteralFieldValueB> UP;
    typedef string value_type;

    LiteralFieldValueB() :
        FieldValue(),
        _value(),
        _backing(),
        _altered(true)
    {
        _value = _backing;
    }

    LiteralFieldValueB(const LiteralFieldValueB &);
    LiteralFieldValueB(const string& value);

    const value_type & getValue() const { sync(); return _backing; }
    /**
     * Get a ref to the value. If value has recently been deserialized, and
     * never needed as an std::string before, this method lets you get a hold
     * of the data without creating the string.
     */
    stringref getValueRef() const { return _value; }

    LiteralFieldValueB & operator=(const LiteralFieldValueB &);

    void setValueRef(const stringref & value) {
        _value = value;
        _altered = true;
    }

    void setValue(const stringref & value) {
        _backing = value;
        _value = _backing;
        _altered = true;
    }
    virtual size_t hash() const { return vespalib::hashValue(_value.c_str()); }
    void setValue(const char* val, size_t size) { setValue(stringref(val, size)); }

        // FieldValue implementation.
    virtual int compare(const FieldValue& other) const;

    virtual vespalib::string getAsString() const;
    virtual std::pair<const char*, size_t> getAsRaw() const;

    virtual void printXml(XmlOutputStream& out) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual FieldValue& assign(const FieldValue&);
    virtual bool hasChanged() const { return _altered; }

    virtual FieldValue& operator=(const vespalib::stringref &);
    virtual FieldValue& operator=(int32_t);
    virtual FieldValue& operator=(int64_t);
    virtual FieldValue& operator=(float);
    virtual FieldValue& operator=(double);
protected:
    void syncBacking() const __attribute__((noinline));
    void sync() const {
        if (__builtin_expect(_backing.c_str() != _value.c_str(), false)) {
            syncBacking();
        }
    }
    mutable stringref _value;
    mutable string    _backing; // Lazily set when needed
    mutable bool      _altered; // Set if altered after deserialization
private:
    virtual bool getAddZeroTerm() const = 0;
};

template<typename SubClass, int type, bool addZeroTerm>
class LiteralFieldValue : public LiteralFieldValueB {
private:
    virtual bool getAddZeroTerm() const { return addZeroTerm; }
public:
    typedef std::unique_ptr<SubClass> UP;

    LiteralFieldValue() : LiteralFieldValueB() { }
    LiteralFieldValue(const string& value) : LiteralFieldValueB(value) { }
    virtual const DataType *getDataType() const;
};

template<typename SubClass, int type, bool addZeroTerm>
const DataType *
LiteralFieldValue<SubClass, type, addZeroTerm>::getDataType() const
{
    switch (type) {
    case DataType::T_URI:    return DataType::URI;
    case DataType::T_STRING: return DataType::STRING;
    case DataType::T_RAW:    return DataType::RAW;
    default:
        throw vespalib::IllegalStateException(vespalib::make_string(
                        "Illegal literal type id %i", type), VESPA_STRLOC);
    }
}

} // document

