// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exceptions.h>
#include <memory>

namespace document {

class BufferOutOfBoundsException : public vespalib::IoException {
    static std::string createMessage(size_t pos, size_t len);
public:
    BufferOutOfBoundsException(size_t pos, size_t len,
                               const std::string& location = "");

    VESPA_DEFINE_EXCEPTION_SPINE(BufferOutOfBoundsException)
};

}

