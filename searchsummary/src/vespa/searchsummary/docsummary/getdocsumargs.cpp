// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocsumargs.h"

namespace search::docsummary {

GetDocsumArgs::GetDocsumArgs()
    : _resultClassName(),
      _dumpFeatures(false),
      _locations_possible(true),
      _stackDump(),
      _location(),
      _timeout(30s),
      _highlightTerms()
{ }


GetDocsumArgs::~GetDocsumArgs() = default;

void
GetDocsumArgs::initFromDocsumRequest(const engine::DocsumRequest &req)
{
    _dumpFeatures       = req.dumpFeatures;
    _resultClassName    = req.resultClassName;
    _stackDump          = req.stackDump;
    _location           = req.location;
    _locations_possible = true;
    _timeout            = req.getTimeLeft();
    _highlightTerms     = req.propertiesMap.highlightTerms();
}

void
GetDocsumArgs::SetStackDump(uint32_t stackDumpLen, const char *stackDump)
{
    _stackDump.resize(stackDumpLen);
    memcpy(&_stackDump[0], stackDump, _stackDump.size());
}

}
