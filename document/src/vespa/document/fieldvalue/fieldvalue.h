// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::FieldValue
 * \ingroup fieldvalue
 *
 * \brief Wraps values stored in documents.
 *
 * This class is a superclass for all values that can be stored within a
 * document. A field value stores data as defined by the datatype belonging
 * to the value.
 */
#pragma once

#include "fieldvaluevisitor.h"
#include "modificationstatus.h"
#include <vespa/document/util/identifiableid.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/vespalib/objects/identifiable.h>
#include <vespa/vespalib/util/polymorphicarraybase.h>

namespace vespalib { class nbostream; }
namespace vespalib::xml { class XmlOutputStream; }

namespace document::fieldvalue { class IteratorHandler; }

namespace document {

class DataType;

class FieldValue
{
public:
    enum class Type : uint8_t {
        NONE, BOOL, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE,
        STRING, RAW, PREDICATE, TENSOR, ANNOTATION_REFERENCE,
        REFERENCE, ARRAY, WSET, MAP, STRUCT, DOCUMENT
    };
    using PathRange = FieldPath::Range<FieldPath::const_iterator>;
    using UP = std::unique_ptr<FieldValue>;
    using XmlOutputStream = vespalib::xml::XmlOutputStream;

    virtual ~FieldValue() = default;
    /**
     * Visit this fieldvalue for double dispatch.
     */
    virtual void accept(FieldValueVisitor &visitor) = 0;
    virtual void accept(ConstFieldValueVisitor &visitor) const = 0;

    /**
     * operator= is only implemented for leaf types, such that they are
     * checked at compile time. assign() here can be used to assign potentially
     * any value to this field value. It will check whether type is supported
     * at runtime. Note that operator= has some overloaded functions allowing
     * to assign primitives to fieldvalues too.
     *
     * @throw vespalib::IllegalArgumentException If type of value not compatible
     */
    virtual FieldValue& assign(const FieldValue&);

    /** Get the datatype describing what can be stored in this fieldvalue. */
    virtual const DataType *getDataType() const = 0;

    void serialize(vespalib::nbostream &stream) const;
    vespalib::nbostream serialize() const;

    /**
     * Compares this fieldvalue with another fieldvalue.
     * Should return 0 if the two are equal, <0 if this object is "less" than
     * the other, and >0 if this object is more than the other.
     */
    virtual int compare(const FieldValue& other) const;

    /**
     * Same as normal compar, but this one expects the types to be equal
     * @param other
     * @return See compare
     */
    virtual int fastCompare(const FieldValue& other) const;

    /** Cloneable implementation */
    virtual FieldValue* clone() const = 0;

      // Utility methods to be able to compare values easily
    bool operator>(const FieldValue& v)  const { return (compare(v) > 0); }
    bool operator>=(const FieldValue& v) const { return (compare(v) >= 0); }
    bool operator==(const FieldValue& v) const { return (compare(v) == 0); }
    bool operator<=(const FieldValue& v) const { return (compare(v) <= 0); }
    bool operator<(const FieldValue& v)  const { return (compare(v) < 0); }
    bool operator!=(const FieldValue& v) const { return (compare(v) != 0); }
    virtual size_t hash() const;

    /** Override toXml from XmlSerializable to add start/stop tags. */
    virtual std::string toXml(const std::string& indent = "") const;

    // Utility functions to unwrap field values if you know the type.

    /**
     * @return Returns the wrapped value if it is a byte or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual char getAsByte() const;

    /**
     * @return Returns the wrapped value if it is an int or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual int32_t getAsInt() const;

    /**
     * @return Returns the wrapped value if it is a long or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual int64_t getAsLong() const;

    /**
     * @return Returns the wrapped value if it is a float or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual float getAsFloat() const;

    /**
     * @return Returns the wrapped value if it is a double or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual double getAsDouble() const;

    /**
     * @return Returns the wrapped value if it is a string or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual vespalib::string getAsString() const;

    /**
     * @return Returns the wrapped value if it is a raw or compatible type.
     * @throws document::InvalidDataTypeConversionException
     */
    virtual std::pair<const char*, size_t> getAsRaw() const;

    /**
     * Will give you the leaf fieldvalue you are looking for in your fieldPath.
     * If the path does not lead anywhere an empty UP will be returned.
     */
    FieldValue::UP getNestedFieldValue(PathRange nested) const;

    /**
     * Will iterate the possibly nested fieldvalue depth first.
     * It will follow the specifed path with proper invocations of
     * onXXXStart/onXXXEnd.  At end it will iterate the rest below with
     * invocations of the before mentioned methods and the additional
     * onPrimitive.
     */
    fieldvalue::ModificationStatus iterateNested(PathRange nested, fieldvalue::IteratorHandler & handler) const;

    fieldvalue::ModificationStatus iterateNested(const FieldPath& fieldPath, fieldvalue::IteratorHandler& handler) const {
        return iterateNested(fieldPath.begin(), fieldPath.end(), handler);
    }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const = 0;
    /** Utility function to get this output as a string.  */
    std::string toString(bool verbose=false, const std::string& indent="") const;
    virtual void printXml(XmlOutputStream& out) const = 0;

    // Utility functions to set commonly used value types.
    virtual FieldValue& operator=(vespalib::stringref);

    Type type() const noexcept { return _type; }
    bool isA(Type type) const noexcept { return type == _type; }
    bool isCollection() const noexcept { return (_type == Type::WSET) || (_type == Type::ARRAY); }
    bool isStructured() const noexcept { return (_type == Type::DOCUMENT) || (_type == Type::STRUCT); }
    bool isLiteral() const noexcept { return (_type == Type::STRING) || (_type == Type::RAW); }
    bool isNumeric() const noexcept {
        return (_type == Type::BYTE) || (_type == Type::SHORT) ||
               (_type == Type::INT) || (_type == Type::LONG) || (_type == Type::FLOAT) || (_type == Type::DOUBLE);
    }
    bool isFixedSizeSingleValue() const noexcept {
        return (_type == Type::BOOL) || isNumeric();
    }
    const char * className() const noexcept;
protected:
    FieldValue(Type type) noexcept : _type(type) { }
    FieldValue(const FieldValue&) = default;
    FieldValue& operator=(const FieldValue&) = default;
    FieldValue(FieldValue &&) noexcept = default;
    FieldValue& operator=(FieldValue &&) noexcept = default;
    static std::unique_ptr<vespalib::IArrayBase> createArray(const DataType & baseType);
private:
    fieldvalue::ModificationStatus
    iterateNested(FieldPath::const_iterator start, FieldPath::const_iterator end, fieldvalue::IteratorHandler & handler) const {
        return iterateNested(PathRange(start, end), handler);
    }
    virtual FieldValue::UP onGetNestedFieldValue(PathRange nested) const;
    virtual fieldvalue::ModificationStatus onIterateNested(PathRange nested, fieldvalue::IteratorHandler & handler) const;

    Type  _type;
};

std::ostream& operator<<(std::ostream& out, const FieldValue & p);

vespalib::xml::XmlOutputStream & operator<<(vespalib::xml::XmlOutputStream & out, const FieldValue & p);

} // document
