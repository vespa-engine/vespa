// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/exception.h>

namespace vespalib {

class JsonStreamException : public Exception {
    string _reason;
public:
    JsonStreamException(std::string_view reason,
                        std::string_view history,
                        std::string_view location, int skipStack = 0);
    std::string_view getReason() const { return _reason; }
    VESPA_DEFINE_EXCEPTION_SPINE(JsonStreamException);
    ~JsonStreamException();
};

}
