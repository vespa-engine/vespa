// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/document/util/serializable.h>

#include <stdio.h>
#include <vespa/document/util/bytebuffer.h>

using vespalib::DefaultAlloc;

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


}
