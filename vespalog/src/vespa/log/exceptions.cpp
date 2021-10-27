// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "exceptions.h"

namespace ns_log {

BadLogLineException::BadLogLineException(std::string message)
    : _message(std::move(message))
{
}

BadLogLineException::~BadLogLineException() = default;

const char*
BadLogLineException::what() const noexcept
{
    return _message.c_str();
}

}
