// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feeddebugger.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace proton {

namespace {

void
setupDebugging(std::vector<uint32_t> & debugLidList)
{
    vespalib::string lidList = vespalib::safe_char_2_string(getenv("VESPA_PROTON_DEBUG_FEED_LID_LIST"));
    vespalib::StringTokenizer lidTokenizer(lidList);
    for (auto token : lidTokenizer) {
        vespalib::asciistream is(token);
        uint32_t lid(0);
        is >> lid;
        debugLidList.push_back(lid);
    }
}

void
setupDebugging(std::vector<document::DocumentId> & debugLidList)
{
    vespalib::string lidList = vespalib::safe_char_2_string(getenv("VESPA_PROTON_DEBUG_FEED_DOCID_LIST"));
    vespalib::StringTokenizer lidTokenizer(lidList);
    for (auto i : lidTokenizer) {
        debugLidList.emplace_back(i);
    }
}

}

FeedDebugger::FeedDebugger()
    : _enableDebugging(false),
      _debugLidList(),
      _debugDocIdList()
{
    setupDebugging(_debugLidList);
    setupDebugging(_debugDocIdList);
    _enableDebugging = ! (_debugLidList.empty() && _debugDocIdList.empty());
}

FeedDebugger::~FeedDebugger() = default;

ns_log::Logger::LogLevel
FeedDebugger::getDebugDebuggerInternal(uint32_t lid, const document::DocumentId * docid) const
{
    for(unsigned int candidate : _debugLidList) {
        if (lid == candidate) {
            return ns_log::Logger::info;
        }
    }
    if (docid != nullptr) {
        for (const auto & i : _debugDocIdList) {
            if (*docid == i) {
                return ns_log::Logger::info;
            }
        }
    }
    return ns_log::Logger::spam;
}

} // namespace proton
