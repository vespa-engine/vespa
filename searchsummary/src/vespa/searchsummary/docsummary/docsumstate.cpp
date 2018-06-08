// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstate.h"
#include <vespa/juniper/rpinterface.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/common/location.h>
#include "docsum_field_writer_state.h"

namespace search {
namespace docsummary {

GetDocsumsState::GetDocsumsState(GetDocsumsStateCallback &callback)
    : _args(),
      _docsumbuf(NULL),
      _docsumcnt(0),
      _kwExtractor(NULL),
      _keywords(NULL),
      _callback(callback),
      _dynteaser(),
      _docSumFieldSpace(_docSumFieldSpaceStore, sizeof(_docSumFieldSpaceStore)), // only alloc buffer if needed
      _attrCtx(),
      _attributes(),
      _fieldWriterStates(),
      _jsonStringer(),
      _parsedLocation(),
      _summaryFeatures(NULL),
      _summaryFeaturesCached(false),
      _rankFeatures(NULL)
{
    _dynteaser._docid    = static_cast<uint32_t>(-1);
    _dynteaser._input    = static_cast<uint32_t>(-1);
    _dynteaser._lang     = static_cast<uint32_t>(-1);
    _dynteaser._config   = NULL;
    _dynteaser._query    = NULL;
    _dynteaser._result   = NULL;
}


GetDocsumsState::~GetDocsumsState()
{
    free(_docsumbuf);
    free(_keywords);
    if (_dynteaser._result != NULL) {
        juniper::ReleaseResult(_dynteaser._result);
    }
    if (_dynteaser._query != NULL) {
        juniper::ReleaseQueryHandle(_dynteaser._query);
    }
}

}
}
