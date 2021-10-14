// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::CollectionFieldValue
 * \ingroup fieldvalue
 *
 * \brief A field value containing a collection of other equally typed values.
 *
 * This class is the superclass of array and weighted set field values. It
 * contains some common functionality.
 */
#pragma once

#include "fieldvalue.h"
#include <vespa/document/datatype/collectiondatatype.h>

namespace document {

class CollectionFieldValue : public FieldValue {
protected:
    const DataType *_type;

        // As overloading doesn't work with polymorphy, have protected functions
        // doing the functionality, such that we can make utility functions here
    virtual bool addValue(const FieldValue&) = 0;
    virtual bool containsValue(const FieldValue&) const = 0;
    virtual bool removeValue(const FieldValue&) = 0;
    void verifyType(const CollectionFieldValue& other) const;

public:
    CollectionFieldValue(const DataType &type)
        : FieldValue(),
          _type(&type)
    {}

    CollectionFieldValue(const CollectionFieldValue& other);
    CollectionFieldValue& operator=(const CollectionFieldValue& other) {
        verifyType(other);
        return *this;
    }

    const DataType *getDataType() const override { return _type; }

    FieldValue::UP createNested() const {
        return getNestedType().createFieldValue();
    }

    const DataType &getNestedType() const {
        return static_cast<const CollectionDataType&>(*_type).getNestedType();
    }

    /**
     * @return True if element was added. False if element was overwritten.
     * @throws InvalidDataTypeException If fieldvalue of wrong type is attempted
     *                                  added.
     */
    bool add(const FieldValue& val) { return addValue(val); }
    bool contains(const FieldValue& val) const { return containsValue(val); }
    /** @return True if element was found and removed. False if not found. */
    bool remove(const FieldValue& val) { return removeValue(val); }

    virtual bool isEmpty() const = 0;
    virtual size_t size() const = 0;
    virtual void clear() = 0;

    // Convenience functions for using primitives directly

    bool add(vespalib::stringref val)
        { return addValue(*createNested() = val); }
    bool add(int32_t val)
        { return addValue(*createNested() = val); }
    bool add(int64_t val)
        { return addValue(*createNested() = val); }
    bool add(float val)
        { return addValue(*createNested() = val); }
    bool add(double val)
        { return addValue(*createNested() = val); }

    bool contains(vespalib::stringref val)
        { return containsValue(*createNested() = val); }
    bool contains(int32_t val)
        { return containsValue(*createNested() = val); }
    bool contains(int64_t val)
        { return containsValue(*createNested() = val); }
    bool contains(float val)
        { return containsValue(*createNested() = val); }
    bool contains(double val)
        { return containsValue(*createNested() = val); }

    bool remove(vespalib::stringref val)
        { return removeValue(*createNested() = val); }
    bool remove(int32_t val)
        { return removeValue(*createNested() = val); }
    bool remove(int64_t val)
        { return removeValue(*createNested() = val); }
    bool remove(float val)
        { return removeValue(*createNested() = val); }
    bool remove(double val)
        { return removeValue(*createNested() = val); }

    DECLARE_IDENTIFIABLE_ABSTRACT(CollectionFieldValue);
};

}

