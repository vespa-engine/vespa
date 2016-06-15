// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/documentaccessornode.h>

namespace search {
namespace expression {

class GetDocIdNamespaceSpecificFunctionNode : public DocumentAccessorNode
{
public:
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    DECLARE_EXPRESSIONNODE(GetDocIdNamespaceSpecificFunctionNode);
    GetDocIdNamespaceSpecificFunctionNode() : _value(new StringResultNode("")) { }
    GetDocIdNamespaceSpecificFunctionNode(ResultNode::UP resultNode) : _value(resultNode.release()) { }
private:
    virtual const ResultNode & getResult() const { return *_value; }
    virtual void onDocType(const document::DocumentType & docType) { (void) docType; }
    virtual void onDoc(const document::Document & doc);
    virtual void onPrepare(bool preserveAccurateTypes) { (void) preserveAccurateTypes; }
    virtual bool onExecute() const { return true; }
    ResultNode::CP _value;
};

}
}

