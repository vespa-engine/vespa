// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>

namespace document {
class Document;
class DocumentId;
class DocumentUpdate;
} // namespace document

namespace document::select {

class Value;
class VariableMap;

/*
 * This class contains information about the current document or feed operation being evaluated in a document
 * select expression. Nodes in the parsed document select expression use the context to access the values needed for
 * evaluating the expression.
 */
class Context {
public:
    Context();
    Context(const Document& doc);
    Context(const DocumentId& docId);
    Context(const DocumentUpdate& docUpdate);
    virtual ~Context();

    void setVariableMap(std::unique_ptr<VariableMap> map);
    std::unique_ptr<Value> getValue(const std::string& value) const;

    const Document*       _doc;       // Document for put feed operation and visiting.
    const DocumentId*     _docId;     // Document id for remove feed operation and visiting.
    const DocumentUpdate* _docUpdate; // Update for partial update feed operation.

private:
    std::unique_ptr<VariableMap> _variables;
};

} // namespace document::select
