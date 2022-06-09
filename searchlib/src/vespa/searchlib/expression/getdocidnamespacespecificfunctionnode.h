// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentaccessornode.h"

namespace search {
namespace expression {

class GetDocIdNamespaceSpecificFunctionNode : public DocumentAccessorNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    DECLARE_EXPRESSIONNODE(GetDocIdNamespaceSpecificFunctionNode);
    GetDocIdNamespaceSpecificFunctionNode() : _value(new StringResultNode("")) { }
    GetDocIdNamespaceSpecificFunctionNode(ResultNode::UP resultNode) : _value(resultNode.release()) { }
private:
    const ResultNode * getResult() const override { return _value.get(); }
    void onDocType(const document::DocumentType & docType) override { (void) docType; }
    void onDoc(const document::Document & doc) override;
    void onPrepare(bool preserveAccurateTypes) override { (void) preserveAccurateTypes; }
    bool onExecute() const override { return true; }
    ResultNode::CP _value;
};

}
}
