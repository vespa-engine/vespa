// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocsumargs.h"
#include <vespa/vespalib/stllike/hash_set_insert.hpp>

namespace search::docsummary {

GetDocsumArgs::GetDocsumArgs()
    : _resultClassName(),
      _dumpFeatures(false),
      _locations_possible(true),
      _stackDump(),
      _location(),
      _timeout(30s),
      _highlightTerms(),
      _fields()
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
    _fields             = FieldSet(req.getFields().begin(), req.getFields().end());
}

void
GetDocsumArgs::setStackDump(uint32_t stackDumpLen, const char *stackDump)
{
    _stackDump.resize(stackDumpLen);
    memcpy(&_stackDump[0], stackDump, _stackDump.size());
}

bool
GetDocsumArgs::need_field(vespalib::stringref field) const {
    return _fields.empty() || _fields.contains(field);
}

}
