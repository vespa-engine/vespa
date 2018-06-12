// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serializable.h"
#include "bufferexceptions.h"
#include "serializableexceptions.h"
#include "bytebuffer.h"

namespace document {

IMPLEMENT_IDENTIFIABLE_ABSTRACT(Serializable, vespalib::Identifiable);
IMPLEMENT_IDENTIFIABLE_ABSTRACT(Deserializable, Serializable);
VESPA_IMPLEMENT_EXCEPTION_SPINE(DeserializeException);
VESPA_IMPLEMENT_EXCEPTION_SPINE(SerializeException);

std::unique_ptr<ByteBuffer> Serializable::serialize() const
{
    size_t len = getSerializedSize();
    std::unique_ptr<ByteBuffer> retVal(new ByteBuffer(len));
    serialize(*retVal.get());
    return retVal;
}

DeserializeException::DeserializeException(const vespalib::string& msg, const vespalib::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, location, 1)
{
}

DeserializeException::DeserializeException(
        const vespalib::string& msg, const vespalib::Exception& cause,
        const vespalib::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, cause, location, 1)
{
}

SerializeException::SerializeException(const vespalib::string& msg, const vespalib::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, location, 1)
{
}

SerializeException::SerializeException(
        const vespalib::string& msg, const vespalib::Exception& cause,
        const vespalib::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, cause, location, 1)
{
}

void
Serializable::serialize(ByteBuffer& buffer) const {
    int pos = buffer.getPos();
    try{
        onSerialize(buffer);
    } catch (...) {
        buffer.setPos(pos);
        throw;
    }
}

void
Deserializable::deserialize(const DocumentTypeRepo &repo, ByteBuffer& buffer) {
    int pos = buffer.getPos();
    try {
        onDeserialize(repo, buffer);
    } catch (const DeserializeException &) {
        buffer.setPos(pos);
        throw;
    } catch (const BufferOutOfBoundsException &) {
        buffer.setPos(pos);
        throw;
    }
}
}
