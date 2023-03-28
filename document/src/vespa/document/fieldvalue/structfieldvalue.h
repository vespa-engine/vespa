// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

/**
 * \class document::StructFieldValue
 * \ingroup fieldvalue
 *
 * \brief Stores a set of predefined field <-> fieldvalue mappings.
 */

#include "structuredfieldvalue.h"
#include "serializablearray.h"

namespace document {

class Document;
class DocumentType;
class DocumentTypeRepo;
struct FieldValueWriter;
class FixedTypeRepo;
class FieldSet;
class StructDataType;

class StructFieldValue final : public StructuredFieldValue
{
private:
    SerializableArray       _fields;
    // As we do lazy deserialization, we need these saved
    const DocumentTypeRepo *_repo;
    const DocumentType     *_doc_type;
    uint16_t                _version;
    mutable bool            _hasChanged;

public:
    using UP = std::unique_ptr<StructFieldValue>;

    explicit StructFieldValue(const DataType &type);
    explicit StructFieldValue(const DocumentTypeRepo& repo, const DataType& type);
    StructFieldValue(const StructFieldValue & rhs);
    StructFieldValue & operator = (const StructFieldValue & rhs);
    StructFieldValue(StructFieldValue && rhs) noexcept = default;
    StructFieldValue & operator = (StructFieldValue && rhs) noexcept = default;
    ~StructFieldValue() noexcept override;

    void setRepo(const DocumentTypeRepo & repo) { _repo = & repo; }
    const DocumentTypeRepo * getRepo() const { return _repo; }
    void setDocumentType(const DocumentType & docType) { _doc_type = & docType; }
    const SerializableArray & getFields() const { return _fields; }

    void lazyDeserialize(const FixedTypeRepo &repo, uint16_t version, SerializableArray::EntryMap && fields, ByteBuffer buffer);

    // returns false if the field could not be serialized.
    bool serializeField(int raw_field_id, uint16_t version, FieldValueWriter &writer) const;
    uint16_t getVersion() const { return _version; }

    // raw_ids may contain ids for elements not in the struct's datatype.
    std::vector<int> getRawFieldIds() const;
    void getRawFieldIds(std::vector<int> &raw_ids, const FieldSet& fieldSet) const;

    void accept(FieldValueVisitor &visitor) override { visitor.visit(*this); }
    void accept(ConstFieldValueVisitor &visitor) const override { visitor.visit(*this); }

    bool hasField(vespalib::stringref name) const override;
    const Field& getField(vespalib::stringref name) const override;
    void clear() override;

    // FieldValue implementation.
    FieldValue& assign(const FieldValue&) override;
    int compare(const FieldValue& other) const override;
    StructFieldValue* clone() const  override{
        return new StructFieldValue(*this);
    }

    void printXml(XmlOutputStream& out) const override;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    bool empty() const override;

    /**
     * Returns true if this object have been altered since last
     * serialization/deserialization. If hasChanged() is false, then cached
     * information from last serialization effort is still valid.
     */
    bool hasChanged() const { return _hasChanged; }

    /**
     * Called by document to reset struct when deserializing where this struct
     * has no content. This clears content and sets changed to false.
     */
    void reset();
private:
    void setFieldValue(const Field&, FieldValue::UP value) override;
    FieldValue::UP getFieldValue(const Field&) const override;
    bool getFieldValue(const Field&, FieldValue&) const override;
    bool hasFieldValue(const Field&) const override;
    void removeFieldValue(const Field&) override;
    VESPA_DLL_LOCAL vespalib::ConstBufferRef getRawField(uint32_t id) const;
    VESPA_DLL_LOCAL const StructDataType & getStructType() const;

    struct FieldIterator;

    StructuredIterator::UP getIterator(const Field* toFind) const override;

    /** Called from Document when deserializing alters type. */
    void setType(const DataType& type) override;
    friend class Document; // Hide from others to prevent misuse
};

} // document
