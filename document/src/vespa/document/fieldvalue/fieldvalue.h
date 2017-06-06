// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/util/xmlserializable.h>
#include <vespa/vespalib/util/polymorphicarrays.h>
#include <vespa/vespalib/objects/cloneable.h>
#include <map>

namespace vespalib {
    class nbostream;
}

namespace document {

class ByteBuffer;

class FieldValue : public vespalib::Identifiable
{
protected:
    FieldValue(const FieldValue&) = default;
    FieldValue& operator=(const FieldValue&) = default;
    using IArray = vespalib::IArrayT<FieldValue>;
    static IArray::UP createArray(const DataType & baseType);

public:
    using PathRange = FieldPath::Range<FieldPath::const_iterator>;
    using UP = std::unique_ptr<FieldValue>;
    using SP = std::shared_ptr<FieldValue>;
    using CP = vespalib::CloneablePtr<FieldValue>;

    class IteratorHandler {
    public:
        class CollectionScope {
        public:
            CollectionScope(IteratorHandler& handler, const FieldValue& value)
                : _handler(handler), _value(value)
            {
                _handler.handleCollectionStart(_value);
            }

            ~CollectionScope() {
                _handler.handleCollectionEnd(_value);
            }
        private:
            IteratorHandler& _handler;
            const FieldValue& _value;
        };

        class StructScope {
        public:
            StructScope(IteratorHandler& handler, const FieldValue& value)
                : _handler(handler), _value(value)
            {
                _handler.handleStructStart(_value);
            }

            ~StructScope() {
                _handler.handleStructEnd(_value);
            }
        private:
            IteratorHandler& _handler;
            const FieldValue& _value;
        };

        class IndexValue {
        public:
            IndexValue() : index(-1), key() {}
            IndexValue(int index_) : index(index_), key() {}
            IndexValue(const FieldValue& key_);
            ~IndexValue();

            vespalib::string toString() const;

            bool operator==(const IndexValue& other) const;

            int index; // For array
            FieldValue::CP key; // For map/wset
        };

        enum ModificationStatus {
            MODIFIED,
            REMOVED,
            NOT_MODIFIED
        };

        typedef std::map<vespalib::string, IndexValue> VariableMap;
    protected:
        class Content {
        public:
            Content(const FieldValue & fv, int weight=1) : _fieldValue(fv), _weight(weight) { }
            int getWeight()               const { return _weight; }
            const FieldValue & getValue() const { return _fieldValue; }
        private:
            const FieldValue & _fieldValue;
            int                _weight;
        };
        IteratorHandler() : _weight(1) { }
    public:
        virtual ~IteratorHandler();

        void handlePrimitive(uint32_t fid, const FieldValue & fv);

        /**
           Handles a complex type (struct/array/map etc) that is at the end of the
           field path.
           @return Return true if you want to recurse into the members.
        */
        bool handleComplex(const FieldValue& fv);
        void handleCollectionStart(const FieldValue & fv);
        void handleCollectionEnd(const FieldValue & fv);
        void handleStructStart(const FieldValue & fv);
        void handleStructEnd(const FieldValue & fv);
        void setWeight(int weight) { _weight = weight; }
        ModificationStatus modify(FieldValue& fv) { return doModify(fv); }

        VariableMap& getVariables() { return _variables; }
        void setVariables(const VariableMap& vars) { _variables = vars; }
        static std::string toString(const VariableMap& vars);
        virtual bool createMissingPath() const { return false; }
    private:
        virtual bool onComplex(const Content& fv) { (void) fv; return true; }
        virtual void onPrimitive(uint32_t fid, const Content & fv);
        virtual void onCollectionStart(const Content & fv) { (void) fv; }
        virtual void onCollectionEnd(const Content & fv) { (void) fv; }
        virtual void onStructStart(const Content & fv) { (void) fv; }
        virtual void onStructEnd(const Content & fv) { (void) fv; }
        virtual ModificationStatus doModify(FieldValue&) { return NOT_MODIFIED; };

        // Scratchpad to store pass on weight.
        int getWeight() const  { return _weight; }
        int _weight;
        VariableMap _variables;
    };

    DECLARE_IDENTIFIABLE_ABSTRACT(FieldValue);

    FieldValue() {}

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

    /** Wrapper for datatypes isA() function. See DataType. */
    virtual bool isA(const FieldValue& other) const;

    void serialize(vespalib::nbostream &stream) const;
    void serialize(ByteBuffer& buffer) const;
    std::unique_ptr<ByteBuffer> serialize() const;

    /**
     * Compares this fieldvalue with another fieldvalue.
     * Should return 0 if the two are equal, <0 if this object is "less" than
     * the other, and >0 if this object is more than the other.
     */
    virtual int compare(const FieldValue& other) const;

    /**
     * Returns true if this object have been altered since last
     * serialization/deserialization. If hasChanged() is false, then cached
     * information from last serialization effort is still valid.
     */
    virtual bool hasChanged() const = 0;

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

    // Utility functions to set commonly used value types.
    virtual FieldValue& operator=(const vespalib::stringref &);
    virtual FieldValue& operator=(int32_t);
    virtual FieldValue& operator=(int64_t);
    virtual FieldValue& operator=(float);
    virtual FieldValue& operator=(double);

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
    IteratorHandler::ModificationStatus iterateNested(PathRange nested, IteratorHandler & handler) const;

    IteratorHandler::ModificationStatus iterateNested(const FieldPath& fieldPath, IteratorHandler& handler) const {
        return iterateNested(fieldPath.begin(), fieldPath.end(), handler);
    }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const = 0;
    // Duplication to reduce size of FieldValue
    void print(std::ostream& out) const { print(out, false, ""); }
    void print(std::ostream& out, bool verbose) const { print(out, verbose, ""); }
    void print(std::ostream& out, const std::string& indent) const { print(out, false, indent); }
    /** Utility function to get this output as a string.  */
    std::string toString(bool verbose=false, const std::string& indent="") const;
    virtual void printXml(XmlOutputStream& out) const = 0;

private:
    IteratorHandler::ModificationStatus
    iterateNested(FieldPath::const_iterator start, FieldPath::const_iterator end, IteratorHandler & handler) const {
        return iterateNested(PathRange(start, end), handler);
    }
    virtual FieldValue::UP onGetNestedFieldValue(PathRange nested) const;
    virtual IteratorHandler::ModificationStatus onIterateNested(PathRange nested, IteratorHandler & handler) const;
};

std::ostream& operator<<(std::ostream& out, const FieldValue & p);

XmlOutputStream & operator<<(XmlOutputStream & out, const FieldValue & p);

} // document
