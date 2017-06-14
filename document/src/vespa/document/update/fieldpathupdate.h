// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "updatevisitor.h"
#include "valueupdate.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/util/serializable.h>
#include <vespa/document/util/xmlserializable.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/select/node.h>
#include <vespa/document/select/resultlist.h>
#include <vespa/vespalib/objects/cloneable.h>

namespace document {

class ByteBuffer;
class DocumentTypeRepo;
class Field;
class FieldValue;
class BucketIdFactory;

class FieldPathUpdate : public vespalib::Cloneable,
                        public Printable,
                        public vespalib::Identifiable
{
protected:
    typedef vespalib::stringref stringref;
    /** To be used for deserialization */
    FieldPathUpdate();

   static vespalib::string getString(ByteBuffer& buffer);
public:
    typedef select::ResultList::VariableMap VariableMap;
    typedef std::shared_ptr<FieldPathUpdate> SP;
    typedef vespalib::CloneablePtr<FieldPathUpdate> CP;

    FieldPathUpdate(const DocumentTypeRepo& repo,
                    const DataType& type,
                    stringref fieldPath,
                    stringref whereClause = stringref());

    ~FieldPathUpdate();

    enum FieldPathUpdateType {
        Add    = IDENTIFIABLE_CLASSID(AddFieldPathUpdate),
        Assign = IDENTIFIABLE_CLASSID(AssignFieldPathUpdate),
        Remove = IDENTIFIABLE_CLASSID(RemoveFieldPathUpdate)
    };

    void applyTo(Document& doc) const;

    FieldPathUpdate* clone() const override = 0;

    virtual bool operator==(const FieldPathUpdate& other) const;
    bool operator!=(const FieldPathUpdate& other) const {
        return ! (*this == other);
    }

    const FieldPath& getFieldPath() const { return *_fieldPath; }
    const select::Node& getWhereClause() const { return *_whereClause; }

    const vespalib::string& getOriginalFieldPath() const { return _originalFieldPath; }
    const vespalib::string& getOriginalWhereClause() const { return _originalWhereClause; }

    /**
     * Check that a given field value is of the type inferred by
     * the field path.
     * @throws IllegalArgumentException upon datatype mismatch.
     */
    void checkCompatibility(const FieldValue& fv) const;

    /** @return Whether or not the first field path element is a body field */
    bool affectsDocumentBody() const;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_IDENTIFIABLE_ABSTRACT(FieldPathUpdate);

    virtual void accept(UpdateVisitor &visitor) const = 0;
    virtual uint8_t getSerializedType() const = 0;

    /** Deserializes and creates a new FieldPathUpdate instance.
     * Requires type id to be not yet read.
     */
    static std::unique_ptr<FieldPathUpdate> createInstance(
            const DocumentTypeRepo& repo,
            const DataType &type, ByteBuffer& buffer,
            int serializationVersion);

protected:
    /**
     * Deserializes the given byte buffer into an instance of a FieldPathUpdate
     * object.
     *
     * @param type A data type that describes the content of the buffer.
     * @param buffer The byte buffer that contains the serialized object.
     * @param version The serialization version of the object to deserialize.
     */
    virtual void deserialize(const DocumentTypeRepo& repo, const DataType& type,
                             ByteBuffer& buffer, uint16_t version);

    /** @return the datatype of the last path element in the field path */
    const DataType& getResultingDataType() const;
    enum SerializedMagic {AssignMagic=0, RemoveMagic=1, AddMagic=2};
private:
    // TODO: rename to createIteratorHandler?
    virtual std::unique_ptr<fieldvalue::IteratorHandler> getIteratorHandler(Document& doc) const = 0;

    vespalib::string _originalFieldPath;
    vespalib::string _originalWhereClause;

    vespalib::CloneablePtr<FieldPath> _fieldPath;
    std::shared_ptr<select::Node> _whereClause;
};

} // ns document

