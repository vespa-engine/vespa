// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocsumargs.h"
#include "resultconfig.h"

namespace search {
namespace docsummary {

GetDocsumArgs::GetDocsumArgs()
    : _ranking(),
      _qflags(0),
      _resultClassName(),
      _stackItems(0),
      _stackDump(),
      _location(),
      _timeout(30 * fastos::TimeStamp::SEC),
      _flags(0u),
      _propertiesMap(),
      _isLocationSet(false)
{ }


GetDocsumArgs::~GetDocsumArgs() { }

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
GetDocsumArgs::Reset()
{
    _ranking.clear();
    _qflags             = 0;
    _stackItems         = 0;
    _timeout            = 30 * fastos::TimeStamp::SEC;
    _flags = 0;
    _resultClassName.clear();
    _stackDump.clear();
    _location.clear();
    _isLocationSet = false;
    {
        PropsMap tmp;
        std::swap(_propertiesMap, tmp);
    }
}


void
GetDocsumArgs::Copy(GetDocsumArgs *src)
{
    if (src == this) {
        return;
    }
    *src = *this;
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
    _flags             = req._flags;
    _propertiesMap     = req.propertiesMap;
    _isLocationSet = (_location.size() > 0);
}

void
GetDocsumArgs::SetStackDump(uint32_t stackItems, uint32_t stackDumpLen, const char *stackDump)
{
    _stackItems = stackItems;
    _stackDump.resize(stackDumpLen);
    memcpy(&_stackDump[0], stackDump, _stackDump.size());
}

}
}
