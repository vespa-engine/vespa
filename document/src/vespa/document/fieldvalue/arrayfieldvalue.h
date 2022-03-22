// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include "collectionfieldvalue.h"
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/vespalib/util/polymorphicarray.h>

namespace document {

class ArrayFieldValue final : public CollectionFieldValue {
private:
    using IArray = vespalib::IArrayT<FieldValue>;
    std::unique_ptr<IArray> _array;

    bool addValue(const FieldValue&) override;
    bool containsValue(const FieldValue& val) const override;
    bool removeValue(const FieldValue& val) override;
    fieldvalue::ModificationStatus iterateSubset(
            int startPos, int endPos, vespalib::stringref variable,
            PathRange nested,
            fieldvalue::IteratorHandler& handler) const;
    fieldvalue::ModificationStatus onIterateNested(PathRange nested, fieldvalue::IteratorHandler & handler) const override;
public:
    using const_iterator = IArray::const_iterator;
    using iterator = IArray::iterator;
    using UP = std::unique_ptr<ArrayFieldValue>;

    /**
     * @param arrayType Type of the array. Must be an ArrayDataType, but does
     *                  not enforce type compile time so it will be easier to
     *                  create instances using field's getDataType().
     */
    ArrayFieldValue(const DataType &arrayType);
    ArrayFieldValue(const ArrayFieldValue&);
    ~ArrayFieldValue();

    ArrayFieldValue& operator=(const ArrayFieldValue&);

    const FieldValue& operator[](uint32_t index) const { return array()[index]; }
    FieldValue& operator[](uint32_t index) { return array()[index]; }

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    void append(FieldValue::UP value) { _array->push_back(*value); }
    void remove(uint32_t index);
    bool remove(const FieldValue& val) { return removeValue(val); }

    bool isEmpty() const override { return _array->empty(); }
    size_t size() const override { return _array->size(); }
    void clear() override { _array->clear(); }
    void reserve(size_t sz) { _array->reserve(sz); }
    void resize(size_t sz) { _array->resize(sz); }

    FieldValue& assign(const FieldValue&) override;
    ArrayFieldValue* clone() const override { return new ArrayFieldValue(*this); }
    int compare(const FieldValue&) const override;
    void printXml(XmlOutputStream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    void swap(ArrayFieldValue & other) { _array.swap(other._array); }

        // Iterator functionality
    const_iterator begin() const { return array().begin(); }
    const_iterator end() const { return array().end(); }

private:
    iterator begin() { return array().begin(); }
    iterator end() { return array().end(); }
    const IArray & array() const { return *_array; }
    IArray & array() { return *_array; }
};

} // document

