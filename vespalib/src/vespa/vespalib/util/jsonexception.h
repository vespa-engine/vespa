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
    VESPA_DEFINE_EXCEPTION_SPINE(JsonStreamException);
    JsonStreamException(const JsonStreamException &);
    JsonStreamException & operator = (const JsonStreamException &);
    JsonStreamException(JsonStreamException &&) noexcept = default;
    JsonStreamException & operator = (JsonStreamException &&) noexcept = default;
    ~JsonStreamException() override;

    std::string_view getReason() const noexcept { return _reason; }
};

}
