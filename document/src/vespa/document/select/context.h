// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace document{

class Document;
class DocumentId;
class DocumentUpdate;

namespace select {

class Context
{
public:
    typedef vespalib::hash_map<vespalib::string, double> VariableMap;

    Context();
    Context(const Document& doc);
    Context(const DocumentId& docId);
    Context(const DocumentUpdate& docUpdate);
    virtual ~Context();

    const Document       * _doc;
    const DocumentId     * _docId;
    const DocumentUpdate * _docUpdate;
    VariableMap            _variables;
};

} // select
} // document


