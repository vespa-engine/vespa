// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentaccessornode.h"

namespace search {
namespace expression {

class GetYMUMChecksumFunctionNode : public DocumentAccessorNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    DECLARE_EXPRESSIONNODE(GetYMUMChecksumFunctionNode);
private:
    void onPrepare(bool preserveAccurateTypes) override { (void) preserveAccurateTypes; }
    const ResultNode * getResult() const override { return &_checkSum; }
    void onDocType(const document::DocumentType & docType) override { (void) docType; }
    void onDoc(const document::Document & doc) override;
    bool onExecute() const override { return true; }
    Int64ResultNode _checkSum;
};

}
}

