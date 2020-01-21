// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exceptions.h>
#include <memory>

namespace document {

class BufferOutOfBoundsException : public vespalib::IoException {
    static vespalib::string createMessage(size_t pos, size_t len);
public:
    BufferOutOfBoundsException(size_t pos, size_t len,
                               const vespalib::string& location = "");

    VESPA_DEFINE_EXCEPTION_SPINE(BufferOutOfBoundsException)
};

}

