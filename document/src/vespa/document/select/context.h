// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace document
{

class Document;
class DocumentId;
class DocumentUpdate;

namespace select
{

class Value;

class Context
{
public:
    typedef vespalib::hash_map<vespalib::string, double> VariableMap;

    Context(void)
        : _doc(NULL),
          _docId(NULL),
          _docUpdate(NULL),
          _variables()
    {
    }

    Context(const Document& doc)
        : _doc(&doc),
          _docId(NULL),
          _docUpdate(NULL),
          _variables()
    {
    }
    
    Context(const DocumentId& docId)
        : _doc(NULL),
          _docId(&docId),
          _docUpdate(NULL),
          _variables()
    {
    }
    
    Context(const DocumentUpdate& docUpdate)
        : _doc(NULL),
          _docId(NULL),
          _docUpdate(&docUpdate),
          _variables()
    {
    }

    virtual
    ~Context(void)
    {
    }

    const Document* _doc;
    const DocumentId* _docId;
    const DocumentUpdate* _docUpdate;
    VariableMap _variables;
};

} // select
} // document


