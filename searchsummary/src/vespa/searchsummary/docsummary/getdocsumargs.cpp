// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "getdocsumargs.h"
#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/vespalib/stllike/hash_set_insert.hpp>

namespace search::docsummary {

GetDocsumArgs::GetDocsumArgs()
    : _resultClassName(),
      _dumpFeatures(false),
      _locations_possible(true),
      _serializedQueryTree(),
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
    if (const auto * queryTree = req.getSerializedQueryTree()) {
        _serializedQueryTree = queryTree->shared_from_this();
    }
    _location           = req.location;
    _locations_possible = true;
    _timeout            = req.getTimeLeft();
    _highlightTerms     = req.propertiesMap.highlightTerms();
    _fields             = FieldSet(req.getFields().begin(), req.getFields().end());
}

bool
GetDocsumArgs::need_field(std::string_view field) const {
    return _fields.empty() || _fields.contains(field);
}

}
