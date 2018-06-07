// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::DataType
 * \ingroup datatype
 *
 * \brief Specifies what is legal to store in a given field value.
 */
#pragma once

#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/vespalib/objects/identifiable.h>

#include <vespa/document/util/identifiableid.h>
#include <vespa/document/util/printable.h>

namespace document {

class FieldValue;
class Field;
class NumericDataType;
class PrimitiveDataType;
class DocumentType;
class WeightedSetDataType;
class FieldPath;

class DataType : public vespalib::Cloneable,
                 public Printable,
                 public vespalib::Identifiable
{
    int _dataTypeId;
    vespalib::string _name;

protected:
    DataType();
    /**
     * Creates a datatype. Note that datatypes must be configured to work with
     * the entire system, you can't just create them on the fly and expect
     * everyone to be able to use them. Only tests and the type manager reading
     * config should need to create datatypes.
     */
    DataType(const vespalib::stringref& name, int dataTypeId);

    /**
     * Creates a datatype using the hash of name as the id.
     */
    explicit DataType(const vespalib::stringref& name);

public:
    virtual ~DataType();
    typedef std::unique_ptr<DataType> UP;
    typedef std::shared_ptr<DataType> SP;
    typedef vespalib::CloneablePtr<DataType> CP;

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
    static const DataType *const STRING;
    static const DataType *const RAW;
    static const DocumentType *const DOCUMENT;
    static const DataType *const TAG;
    static const DataType *const URI;
    static const DataType *const PREDICATE;
    static const DataType *const TENSOR;

    /** Used by type manager to fetch default types to register. */
    static std::vector<const DataType *> getDefaultDataTypes();


    const vespalib::string& getName() const { return _name; }
    int getId() const { return _dataTypeId; }
    bool isValueType(const FieldValue & fv) const;

    /**
     * Create a field value using this datatype.
     */
    virtual std::unique_ptr<FieldValue> createFieldValue() const = 0;
    virtual DataType* clone() const override = 0;

    /**
     * Whether another datatype is a supertype of this one. Document types may
     * be due to inheritance. For other types, they must be identical for this
     * to match.
     */
    virtual bool isA(const DataType& other) const { return (*this == other); }

    virtual bool operator==(const DataType&) const;
    virtual bool operator<(const DataType&) const;
    bool operator != (const DataType & rhs) const { return !(*this == rhs); }

    /**
     * This takes a . separated fieldname and gives you back the path of
     * fields you have to apply to get to your leaf.
     * @param remainFieldName. The remaining part of the fieldname that you want the path of.
     * @return pointer to field path or null if an error occured
     */
    void buildFieldPath(FieldPath & fieldPath, const vespalib::stringref & remainFieldName) const;

    DECLARE_IDENTIFIABLE_ABSTRACT(DataType);
private:
    virtual void onBuildFieldPath(FieldPath & fieldPath, const vespalib::stringref & remainFieldName) const = 0;
};

} // document

