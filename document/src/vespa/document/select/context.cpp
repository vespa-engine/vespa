// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "context.h"

namespace document {
namespace select {

Context::Context(void)
    : _doc(NULL),
      _docId(NULL),
      _docUpdate(NULL),
      _variables()
{ }

Context::Context(const Document& doc)
    : _doc(&doc),
      _docId(NULL),
      _docUpdate(NULL),
      _variables()
{ }

Context::Context(const DocumentId& docId)
    : _doc(NULL),
      _docId(&docId),
      _docUpdate(NULL),
      _variables()
{ }

Context::Context(const DocumentUpdate& docUpdate)
    : _doc(NULL),
      _docId(NULL),
      _docUpdate(&docUpdate),
      _variables()
{ }

Context::~Context() { }

} // select
} // document
