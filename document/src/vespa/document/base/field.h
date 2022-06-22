// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

#include <vespa/vespalib/stllike/string.h>
#include <vespa/document/fieldset/fieldset.h>
#include <vector>

namespace document {

class FieldValue;
class DataType;

class Field final : public FieldSet
{
    vespalib::string _name;
    const DataType *_dataType;
    int             _fieldId;
public:
    using CSP = std::shared_ptr<const Field>;
    using SP = std::shared_ptr<Field>;
    using CPtr = const Field *;

    struct FieldPtrLess {
        bool operator()(CPtr f1, CPtr f2) const {
            return (*f1 < *f2);
        }
    };

    struct FieldPtrEqual {
        bool operator()(CPtr f1, CPtr f2) const {
            return (*f1 == *f2);
        }
    };

    class Set {
    public:
        class Builder {
        public:
            Builder & reserve(size_t sz) { _vector.reserve(sz); return *this; }
            Builder & add(CPtr field) { _vector.push_back(field); return *this; }
            Set build() { return Set(std::move(_vector)); }
        private:
            std::vector<CPtr> _vector;
        };
        bool contains(const Field & field) const;
        bool contains(const Set & field) const;
        size_t size() const { return _fields.size(); }
        bool empty() const { return _fields.empty(); }
        const CPtr * begin() const { return _fields.data(); }
        const CPtr * end() const { return begin() + _fields.size(); }
        static Set emptySet() { return Builder().build(); }
    private:
        explicit Set(std::vector<CPtr> fields);
        std::vector<CPtr> _fields;
    };

    /**
     * Creates a completely specified field instance.
     *
     * @param name The name of the field.
     * @param fieldId The numeric ID representing the field.
     * @param type The datatype of the field.
     * @param headerField Whether or not this is a "header" field.
     */
    Field(vespalib::stringref name, int fieldId, const DataType &type);

    Field();

    /**
     * Creates a completely specified field instance. Field ids are generated
     * by hash function.
     *
     * @param name The name of the field.
     * @param dataType The datatype of the field.
     * @param headerField Whether or not this is a "header" field.
     */
    Field(vespalib::stringref name, const DataType &dataType);

    ~Field() override;

    std::unique_ptr<FieldValue> createValue() const;

    // Note that only id is checked for equality.
    bool operator==(const Field & other) const noexcept { return (_fieldId == other._fieldId); }
    bool operator!=(const Field & other) const noexcept { return (_fieldId != other._fieldId); }
    bool operator<(const Field & other) const noexcept { return (_name < other._name); }

    const DataType &getDataType() const { return *_dataType; }

    int getId() const noexcept { return _fieldId; }
    const vespalib::string & getName() const noexcept { return _name; }

    vespalib::string toString(bool verbose=false) const;
    bool contains(const FieldSet& fields) const override;
    Type getType() const override { return Type::FIELD; }
    bool valid() const noexcept { return _fieldId != 0; }
    uint32_t hash() const noexcept { return getId(); }
private:
    int calculateIdV7();

    void validateId(int newId);
};

} // document

