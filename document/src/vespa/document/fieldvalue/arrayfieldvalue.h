// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::ArrayFieldValue
 * \ingroup fieldvalue
 *
 * \brief A representation of an array of fieldvalues of specific types.
 *
 * A field value representing an array of other fieldvalues of a given type.
 *
 * \see datatype.h
 * \see field.h
 */
#pragma once

#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/fieldvalue/collectionfieldvalue.h>

namespace document {

class ArrayFieldValue : public CollectionFieldValue {
private:
    IArray::UP _array;

    virtual bool addValue(const FieldValue&);
    virtual bool containsValue(const FieldValue& val) const;
    virtual bool removeValue(const FieldValue& val);
    IteratorHandler::ModificationStatus iterateSubset(
            int startPos, int endPos, const vespalib::stringref & variable,
            FieldPath::const_iterator nextPos,
            FieldPath::const_iterator end_,
            IteratorHandler& handler) const;
    virtual IteratorHandler::ModificationStatus onIterateNested(
            FieldPath::const_iterator start, FieldPath::const_iterator end,
            IteratorHandler & handler) const;
public:
    typedef IArray::const_iterator const_iterator;
    typedef IArray::iterator iterator;
    typedef std::unique_ptr<ArrayFieldValue> UP;

    /**
     * @param arrayType Type of the array. Must be an ArrayDataType, but does
     *                  not enforce type compile time so it will be easier to
     *                  create instances using field's getDataType().
     */
    ArrayFieldValue(const DataType &arrayType);
    ArrayFieldValue(const ArrayFieldValue&);
    virtual ~ArrayFieldValue();

    ArrayFieldValue& operator=(const ArrayFieldValue&);

    const FieldValue& operator[](uint32_t index) const { return array()[index]; }
    FieldValue& operator[](uint32_t index) { return array()[index]; }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    void append(FieldValue::UP value) { _array->push_back(*value); }
    void remove(uint32_t index);
    bool remove(const FieldValue& val) { return removeValue(val); }

        // CollectionFieldValue implementation
    virtual bool isEmpty() const { return _array->empty(); }
    virtual size_t size() const { return _array->size(); }
    virtual void clear() { _array->clear(); }
    void reserve(size_t sz) { _array->reserve(sz); }
    void resize(size_t sz) { _array->resize(sz); }

        // FieldValue implementation
    virtual FieldValue& assign(const FieldValue&);
    virtual ArrayFieldValue* clone() const
        { return new ArrayFieldValue(*this); }
    virtual int compare(const FieldValue&) const;
    virtual void printXml(XmlOutputStream& out) const;
    virtual void print(std::ostream& out, bool verbose,
                       const std::string& indent) const;
    virtual bool hasChanged() const;
    void swap(ArrayFieldValue & other) { _array.swap(other._array); }

        // Iterator functionality
    const_iterator begin() const { return array().begin(); }
    const_iterator end() const { return array().end(); }
    iterator begin() { return array().begin(); }
    iterator end() { return array().end(); }

    DECLARE_IDENTIFIABLE_ABSTRACT(ArrayFieldValue);
private:
    const IArray & array() const { return *_array; }
    IArray & array() { return *_array; }
};

} // document

