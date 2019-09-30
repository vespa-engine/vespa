// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocsumargs.h"
#include "resultconfig.h"

namespace search::docsummary {

GetDocsumArgs::GetDocsumArgs()
    : _ranking(),
      _qflags(0),
      _resultClassName(),
      _stackItems(0),
      _stackDump(),
      _location(),
      _timeout(30 * fastos::TimeStamp::SEC),
      _propertiesMap(),
      _isLocationSet(false)
{ }


GetDocsumArgs::~GetDocsumArgs() = default;

void
GetDocsumArgs::setTimeout(const fastos::TimeStamp & timeout)
{
    _timeout = timeout;
}

fastos::TimeStamp
GetDocsumArgs::getTimeout() const
{
    return _timeout;
}

void
GetDocsumArgs::initFromDocsumRequest(const search::engine::DocsumRequest &req)
{
    _ranking           = req.ranking;
    _qflags            = req.queryFlags;
    _resultClassName   = req.resultClassName;
    _stackItems        = req.stackItems;
    _stackDump         = req.stackDump;
    _location          = req.location;
    _timeout           = req.getTimeLeft();
    _propertiesMap     = req.propertiesMap;
}

void
GetDocsumArgs::SetStackDump(uint32_t stackItems, uint32_t stackDumpLen, const char *stackDump)
{
    _stackItems = stackItems;
    _stackDump.resize(stackDumpLen);
    memcpy(&_stackDump[0], stackDump, _stackDump.size());
}

}
