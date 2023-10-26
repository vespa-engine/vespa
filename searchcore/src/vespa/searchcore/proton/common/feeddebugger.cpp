// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feeddebugger.h"
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace proton {

namespace {

void setupDebugging(std::vector<uint32_t> & debugLidList)
{
    vespalib::string lidList = getenv("VESPA_PROTON_DEBUG_FEED_LID_LIST");
    vespalib::StringTokenizer lidTokenizer(lidList);
    for (size_t i(0); i < lidTokenizer.size(); i++) {
        vespalib::asciistream is(lidTokenizer[i]);
        uint32_t lid(0);
        is >> lid;
        debugLidList.push_back(lid);
    }
}

void setupDebugging(std::vector<document::DocumentId> & debugLidList)
{
    vespalib::string lidList = getenv("VESPA_PROTON_DEBUG_FEED_DOCID_LIST");
    vespalib::StringTokenizer lidTokenizer(lidList);
    for (size_t i(0); i < lidTokenizer.size(); i++) {
        debugLidList.push_back(document::DocumentId(lidTokenizer[i]));
    }
}

}

FeedDebugger::FeedDebugger() :
    _enableDebugging(false),
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
    for(size_t i(0), m(_debugLidList.size()); i < m; i++) {
        if (lid == _debugLidList[i]) {
            return ns_log::Logger::info;
        }
    }
    if (docid != NULL) {
        for(size_t i(0), m(_debugDocIdList.size()); i < m; i++) {
            if (*docid == _debugDocIdList[i]) {
                return ns_log::Logger::info;
            }
        }
    }
    return ns_log::Logger::spam;
}

} // namespace proton
