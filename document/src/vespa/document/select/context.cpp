// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "context.h"
#include "variablemap.h"
#include <vespa/document/select/value.h>

namespace document::select {

Context::Context()
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

Context::~Context() = default;

std::unique_ptr<Value>
Context::getValue(const vespalib::string & value) const {
    if (_variables) {
        VariableMap::const_iterator iter = _variables->find(value);

        if (iter != _variables->end()) {
            return std::make_unique<FloatValue>(iter->second);
        } else {
            return std::make_unique<FloatValue>(0.0);
        }
    } else {
        return std::make_unique<FloatValue>(0.0);
    }
}

void
Context::setVariableMap(std::unique_ptr<VariableMap> map) {
    _variables = std::move(map);
}

}
