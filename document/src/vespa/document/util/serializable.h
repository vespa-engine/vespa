// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file serializable.h
 * @ingroup document
 *
 * @brief Interfaces to be used for serializing of objects.
 *
 * @author Thomas F. Gundersen, H�kon Humberset
 * @date 2004-03-15
 * @version $Id$
 */

#pragma once

#include <vespa/vespalib/objects/cloneable.h>
#include <vespa/vespalib/objects/identifiable.h>

#include <vespa/document/util/bytebuffer.h>
#include <vespa/document/util/identifiableid.h>

namespace document {
class DocumentTypeRepo;

/**
 * Base class for classes that can be converted into a bytestream,
 * normally used later to create a similar instance.
 */

class Serializable : public vespalib::Identifiable
{
protected:
    virtual void onSerialize(ByteBuffer& buffer) const = 0;
public:
    DECLARE_IDENTIFIABLE_ABSTRACT(Serializable);

    virtual ~Serializable() {}

    /**
     * @return An upper limit to how many bytes serialization of this instance
     *         need, providing instance is not altered before serialization.
     */
    virtual size_t getSerializedSize() const = 0;

    /**
     * Serializes the instance into the buffer given. Use getSerializedSize()
     * before calling this method to be sure buffer is big enough.
     * On success, the given buffers position will be just past the serialized
     * version of this instance, on failure, position will be reset to whatever
     * it was prior to calling this function.
     *
     * @throw SerializeException If for some reason instance cannot be
     *                           serialized.
     * @throw BufferOutOfBoundsException If buffer does not have enough space.
     */
    void serialize(ByteBuffer& buffer) const;

    /**
     * Creates a bytebuffer with enough space to serialize this instance
     * and serialize this instance into it.
     *
     * @return The created bytebuffer, positioned after the serialization.
     *
     * @throw SerializeException If for some reason instance cannot be
     *                           serialized.
     * @throw BufferOutOfBoundsException If buffer does not have enough space.
     */
    std::unique_ptr<ByteBuffer> serialize() const;
};

/**
 * Base class for instances that can be overwritten from a bytestream,
 * given that the bytestream is created from a similar instance.
 */
class Deserializable : public vespalib::Cloneable, public Serializable
{
protected:
    virtual void onDeserialize(const DocumentTypeRepo &repo, ByteBuffer& buffer) = 0;

public:
    DECLARE_IDENTIFIABLE_ABSTRACT(Deserializable);
    virtual ~Deserializable() {}

    /**
     * Overwrite this object with the object represented by the given
     * bytestream. On success, buffer will be positioned after the bytestream
     * representing the instance we've just deserialized, on failure, bytebuffer
     * will be pointing to where it was pointing before calling this function.
     *
     * @throw DeserializeException If read data doesn't represent a legal object
     *                             of this type.
     * @throw BufferOutOfBoundsException If instance wants to read more data
     *                                   than is available in the buffer.
     */
    void deserialize(const DocumentTypeRepo &repo, ByteBuffer& buffer);
};

}

