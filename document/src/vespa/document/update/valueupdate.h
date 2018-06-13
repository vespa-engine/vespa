// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::ValueUpdate
 * @ingroup document
 *
 * @brief Superclass for all types of field value update operations.
 *
 * It declares the interface required for all value updates.
 *
 * Furthermore, this class inherits from Printable without implementing its
 * virtual {@link Printable#print} function, so that all subclasses must also
 * implement a human readable output format.
 *
 * This class is a serializable, serializing its content to a buffer. It is
 * however not a deserializable, as it does not serialize the datatype of the
 * content it serializes, such that it needs to get a datatype specified upon
 * deserialization.
 */
#pragma once

#include "updatevisitor.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/util/xmlserializable.h>

namespace document {

class DocumentTypeRepo;
class Field;
class FieldValue;

class ValueUpdate : public vespalib::Identifiable,
                    public Printable,
                    public vespalib::Cloneable,
                    public XmlSerializable
{
protected:
    using nbostream = vespalib::nbostream;
public:
    using CP = vespalib::CloneablePtr<ValueUpdate>;

    /**
     * Create a value update object from the given byte buffer.
     *
     * @param type A data type that describes the content of the buffer.
     * @param buffer The byte buffer that containes the serialized update.
     */
    static std::unique_ptr<ValueUpdate> createInstance(const DocumentTypeRepo& repo, const DataType& type, nbostream & buffer);

    /** Define all types of value updates. */
    enum ValueUpdateType {
        Add        = IDENTIFIABLE_CLASSID(AddValueUpdate),
        Arithmetic = IDENTIFIABLE_CLASSID(ArithmeticValueUpdate),
        Assign     = IDENTIFIABLE_CLASSID(AssignValueUpdate),
        Clear      = IDENTIFIABLE_CLASSID(ClearValueUpdate),
        Map        = IDENTIFIABLE_CLASSID(MapValueUpdate),
        Remove     = IDENTIFIABLE_CLASSID(RemoveValueUpdate)
    };

    ValueUpdate()
        : Printable(), Cloneable(), XmlSerializable() {}

    virtual ~ValueUpdate() {}

    virtual bool operator==(const ValueUpdate&) const = 0;
    bool operator != (const ValueUpdate & rhs) const { return ! (*this == rhs); }

    /**
     * Recursively checks the compatibility of this value update as
     * applied to the given document field.
     *
     * @throws IllegalArgumentException Thrown if this value update
     *                                  is not compatible.
     */
    virtual void checkCompatibility(const Field& field) const = 0;

    /**
     * Applies this value update to the given field value.
     *
     * @return True if value is updated, false if value should be removed.
     */
    virtual bool applyTo(FieldValue& value) const = 0;

    ValueUpdate* clone() const override = 0;

    /**
     * Deserializes the given byte buffer into an instance of an update object.
     *
     * @param type A data type that describes the content of the buffer.
     * @param buffer The byte buffer that contains the serialized update object.
     */
    virtual void deserialize(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream) = 0;

    /** @return The operation type. */
    ValueUpdateType getType() const {
        return static_cast<ValueUpdateType>(getClass().id());
    }

    /**
     * Visit this fieldvalue for double dispatch.
     */
    virtual void accept(UpdateVisitor &visitor) const = 0;

    DECLARE_IDENTIFIABLE_ABSTRACT(ValueUpdate);
};

} // document

