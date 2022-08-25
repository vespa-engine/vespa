// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DataType
 * \ingroup datatype
 *
 * \brief Specifies what is legal to store in a given field value.
 */
#pragma once

#include <vespa/document/util/printable.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>

namespace document {

class FieldValue;
class Field;
class FieldPath;

class ArrayDataType;
class CollectionDataType;
class DocumentType;
class MapDataType;
class NumericDataType;
class PrimitiveDataType;
class ReferenceDataType;
class TensorDataType;
class WeightedSetDataType;

class DataType : public Printable
{
    int _dataTypeId;
    vespalib::string _name;

protected:
    /**
     * Creates a datatype. Note that datatypes must be configured to work with
     * the entire system, you can't just create them on the fly and expect
     * everyone to be able to use them. Only tests and the type manager reading
     * config should need to create datatypes.
     */
    DataType(vespalib::stringref name, int dataTypeId) noexcept;

    /**
     * Creates a datatype using the hash of name as the id.
     */
    explicit DataType(vespalib::stringref name) noexcept;

public:
    ~DataType() override;
    using UP = std::unique_ptr<DataType>;
    using SP = std::shared_ptr<DataType>;

    /**
     * Enumeration of primitive data type identifiers. (Complex types uses
     * hashed identifiers.
     *
     * <b>NOTE:</b> These types are also defined in the java source (in file
     * "document/src/java/com/yahoo/document/DataType.java". Changes done
     * here must also be applied there.
     */
    enum Type {
        T_INT         =  0,
        T_FLOAT       =  1,
        T_STRING      =  2,
        T_RAW         =  3,
        T_LONG        =  4,
        T_DOUBLE      =  5,
        T_BOOL        =  6,
        T_DOCUMENT    =  8, // Type of super document type Document.0 that all documents inherit.
        // T_TIMESTAMP   =  9,  // Not used anymore, Id should probably not be reused
        T_URI         = 10,
        // T_EXACTSTRING = 11,  // Not used anymore, Id should probably not be reused
        // T_CONTENT     = 12,  // Not used anymore, Id should probably not be reused
        // T_CONTENTMETA = 13,  // Not used anymore, Id should probably not be reused
        // T_MAILADDRESS = 14,  // Not used anymore, Id should probably not be reused
        // T_TERMBOOST   = 15,  // Not used anymore, Id should probably not be reused
        T_BYTE        = 16,
        T_TAG         = 18,
        T_SHORT       = 19,
        T_PREDICATE   = 20,
        T_TENSOR      = 21,
        MAX
    };

    static const DataType *const BYTE;
    static const DataType *const SHORT;
    static const DataType *const INT;
    static const DataType *const LONG;
    static const DataType *const FLOAT;
    static const DataType *const DOUBLE;
    static const DataType *const BOOL;
    static const DataType *const STRING;
    static const DataType *const RAW;
    static const DocumentType *const DOCUMENT;
    static const DataType *const TAG;
    static const DataType *const URI;
    static const DataType *const PREDICATE;
    static const DataType *const TENSOR;

    /** Used by type manager to fetch default types to register. */
    static std::vector<const DataType *> getDefaultDataTypes();

    const vespalib::string& getName() const noexcept { return _name; }
    int getId() const noexcept { return _dataTypeId; }
    bool isValueType(const FieldValue & fv) const;

    /**
     * Create a field value using this datatype.
     */
    virtual std::unique_ptr<FieldValue> createFieldValue() const = 0;

    virtual bool isWeightedSet() const noexcept { return false; }
    virtual bool isArray() const noexcept { return false; }
    virtual bool isDocument() const noexcept { return false; }
    virtual bool isTensor() const noexcept { return false; }
    virtual bool isPrimitive() const noexcept { return false; }
    virtual bool isNumeric() const noexcept { return false; }
    virtual bool isStructured() const noexcept { return false; }
    virtual const CollectionDataType * cast_collection() const noexcept { return nullptr; }
    virtual const MapDataType * cast_map() const noexcept { return nullptr; }
    virtual const ReferenceDataType * cast_reference() const noexcept { return nullptr; }
    virtual const TensorDataType* cast_tensor() const noexcept { return nullptr; }
    bool isMap() const { return cast_map() != nullptr; }

    /**
     * Whether another datatype is a supertype of this one. Document types may
     * be due to inheritance. For other types, they must be identical for this
     * to match.
     */
    virtual bool isA(const DataType& other) const { return equals(other); }

    virtual bool equals(const DataType & other) const noexcept {
        return _dataTypeId == other._dataTypeId;
    }

    bool operator == (const DataType & other) const noexcept {
        return equals(other);
    }
    int cmpId(const DataType& b) const {
        return (_dataTypeId < b._dataTypeId)
               ? -1
               : (b._dataTypeId < _dataTypeId)
                 ? 1
                 : 0;
    }

    /**
     * This takes a . separated fieldname and gives you back the path of
     * fields you have to apply to get to your leaf.
     * @param remainFieldName. The remaining part of the fieldname that you want the path of.
     *                         MUST be null-terminated.
     * @return pointer to field path or null if an error occured
     */
    void buildFieldPath(FieldPath & fieldPath, vespalib::stringref remainFieldName) const;

    /** @throws FieldNotFoundException if field does not exist. */
    virtual const Field& getField(int fieldId) const;
private:
    virtual void onBuildFieldPath(FieldPath & fieldPath, vespalib::stringref remainFieldName) const = 0;
};

} // document

