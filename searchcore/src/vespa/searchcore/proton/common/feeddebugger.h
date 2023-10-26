// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/documentid.h>
#include <vespa/log/log.h>
#include <vector>

namespace proton {

class FeedDebugger
{
public:
    FeedDebugger();
    ~FeedDebugger();
    bool isDebugging() const { return _enableDebugging; }
    ns_log::Logger::LogLevel getDebugLevel(uint32_t lid, const document::DocumentId & docid) const {
        return getDebugLevel(lid, & docid);
    }
    ns_log::Logger::LogLevel getDebugLevel(uint32_t lid, const document::DocumentId * docid) const {
        if (isDebugging()) {
            return getDebugDebuggerInternal(lid, docid);
        }
        return ns_log::Logger::spam;
    }
private:
    ns_log::Logger::LogLevel getDebugDebuggerInternal(uint32_t lid, const document::DocumentId * docid) const;
    bool                              _enableDebugging;
    std::vector<uint32_t>             _debugLidList; // List of lids to dump when feeding/replaying log.
    std::vector<document::DocumentId> _debugDocIdList; // List of docids("id:ns:doctype::xyz" to dump when feeding/replaying log.
};

} // namespace proton

