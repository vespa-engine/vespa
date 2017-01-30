// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::Field
 * \ingroup base
 *
 * \brief Specifies a field within a structured data type.
 *
 * A structured data type contains a key - value mapping of predefined
 * data types. The field class is the key in these maps, and contains
 * an identifier, in addition to datatype of values.
 */
#pragma once

#include <memory>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldset/fieldset.h>
#include <set>

namespace document {

class FieldValue;

class Field final : public vespalib::FieldBase,
                    public vespalib::Identifiable,
                    public FieldSet
{
    const DataType *_dataType;
    int _fieldId;
    bool _isHeaderField;
public:
    typedef std::shared_ptr<const Field> CSP;
    typedef std::shared_ptr<Field> SP;

    struct FieldPtrComparator {
        bool operator()(const Field* f1, const Field* f2) const {
            return (*f1 < *f2);
        }
    };

    typedef std::set<const Field*, FieldPtrComparator> Set;

    /**
     * Creates a completely specified field instance.
     *
     * @param name The name of the field.
     * @param fieldId The numeric ID representing the field.
     * @param type The datatype of the field.
     * @param headerField Whether or not this is a "header" field.
     */
    Field(const vespalib::stringref & name, int fieldId,
          const DataType &type, bool headerField);

    Field() : Field("", 0, *DataType::INT, false) { }

    /**
     * Creates a completely specified field instance. Field ids are generated
     * by hash function.
     *
     * @param name The name of the field.
     * @param dataType The datatype of the field.
     * @param headerField Whether or not this is a "header" field.
     */
    Field(const vespalib::stringref & name, const DataType &dataType, bool headerField);

    FieldSet* clone() const override { return new Field(*this); }
    std::unique_ptr<FieldValue> createValue() const;

    // Note that only id is checked for equality.
    bool operator==(const Field & other) const { return (_fieldId == other._fieldId); }
    bool operator!=(const Field & other) const { return (_fieldId != other._fieldId); }
    bool operator<(const Field & other) const { return (getName() < other.getName()); }

    const DataType &getDataType() const { return *_dataType; }

    int getId() const { return getId(7); }
    int getId(int version) const { (void) version; return _fieldId; }
    bool isHeaderField() const { return _isHeaderField; }

    vespalib::string toString(bool verbose=false) const;
    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return FIELD; }
private:
    int calculateIdV7();

    void validateId(int newId);
};

} // document

