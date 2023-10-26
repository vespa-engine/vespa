// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document {
    class Document;
    class DocumentId;
    class DocumentUpdate;
}

namespace document::select {

class Value;
class VariableMap;

class Context {
public:
    Context();
    Context(const Document & doc);
    Context(const DocumentId & docId);
    Context(const DocumentUpdate & docUpdate);
    virtual ~Context();

    void setVariableMap(std::unique_ptr<VariableMap> map);
    std::unique_ptr<Value> getValue(const vespalib::string & value) const;

    const Document *_doc;
    const DocumentId *_docId;
    const DocumentUpdate *_docUpdate;
private:
    std::unique_ptr<VariableMap> _variables;
};

}
