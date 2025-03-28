// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace proton {

/**
 * Interface used to deny write operations when resource limits are reached.
 */
struct IResourceWriteFilter
{
    class State
    {
    private:
        bool _acceptWriteOperation;
        std::string _message;
    public:
        State()
            : _acceptWriteOperation(true),
              _message()
        {}
        State(bool acceptWriteOperation_, const std::string &message_)
            : _acceptWriteOperation(acceptWriteOperation_),
              _message(message_)
        {}
        bool acceptWriteOperation() const { return _acceptWriteOperation; }
        const std::string &message() const { return _message; }
    };

    virtual ~IResourceWriteFilter() = default;

    virtual bool acceptWriteOperation() const = 0;
    virtual State getAcceptState() const = 0;
};

} // namespace proton
