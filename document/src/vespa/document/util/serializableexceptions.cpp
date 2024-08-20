// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "serializableexceptions.h"

namespace document {

VESPA_IMPLEMENT_EXCEPTION_SPINE(DeserializeException);

DeserializeException::DeserializeException(const std::string& msg, const std::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, location, 1)
{
}

DeserializeException::DeserializeException(
        const std::string& msg, const vespalib::Exception& cause,
        const std::string& location)
    : IoException(msg, IoException::CORRUPT_DATA, cause, location, 1)
{
}

}
