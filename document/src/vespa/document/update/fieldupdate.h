// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::FieldUpdate
 * \ingroup update
 *
 * \brief Represent a collection of updates to be performed on a single
 * field in a document.
 *
 * It inherits from Printable to produce human readable output when required.
 * Serialization is done through Serializable and XmlSerializable.
 * Deserialization is specially handled as document type is not serialized with
 * the object.
 */
#pragma once

#include "valueupdate.h"
#include <vespa/document/base/field.h>

namespace document {

class Document;
class DocumentType;

class FieldUpdate
{
public:
    using nbostream = vespalib::nbostream;
    using ValueUpdates = std::vector<std::unique_ptr<ValueUpdate>>;
    using XmlOutputStream = vespalib::xml::XmlOutputStream;

    FieldUpdate(const Field& field);
    FieldUpdate(const FieldUpdate &) = delete;
    FieldUpdate & operator = (const FieldUpdate &) = delete;
    FieldUpdate(FieldUpdate &&) = default;
    FieldUpdate & operator = (FieldUpdate &&) = default;
    ~FieldUpdate();

    /**
     * This is a convenience function to construct a field update directly from
     * a stream by deserializing all its content from the stream.
     *
     * @param type A document type that describes the stream content.
     * @param stream A stream that contains a serialized field update.
     */
    FieldUpdate(const DocumentTypeRepo& repo, const DataType & type, nbostream & stream);

    bool operator==(const FieldUpdate&) const;
    bool operator!=(const FieldUpdate & rhs) const { return ! (*this == rhs); }

    /**
     * Add a value update to this field update.
     *
     * @param update A pointer to the value update to add to this.
     * @return A pointer to this.
     */
    FieldUpdate& addUpdate(std::unique_ptr<ValueUpdate> update) &;
    FieldUpdate&& addUpdate(std::unique_ptr<ValueUpdate> update) &&;

    const ValueUpdate& operator[](int index) const { return *_updates[index]; }
    ValueUpdate& operator[](int index) { return *_updates[index]; }
    size_t size() const { return _updates.size(); }

    /** @return The non-modifieable list of value updates to perform. */
    const ValueUpdates & getUpdates() const { return _updates; }

    const Field& getField() const { return _field; }
    void applyTo(Document& doc) const;
    void print(std::ostream& out, bool verbose, const std::string& indent) const;
    void printXml(XmlOutputStream&) const;

    /**
     * Deserializes the given stream into an instance of an update object.
     * Not a Deserializable, as document type is needed as extra information.
     *
     * @param type A document type that describes the stream content.
     * @param buffer The stream that contains the serialized update object.
     */
    void deserialize(const DocumentTypeRepo& repo, const DocumentType& type, nbostream& stream);
private:
    Field _field;
    ValueUpdates _updates;
};

} // document

