// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace slime { struct Inspector; }
}  // namespace vespalib

namespace document {

class PredicateSlimeVisitor {
protected:
    using Inspector = vespalib::slime::Inspector;
    virtual void visitFeatureSet(const Inspector &i) = 0;
    virtual void visitFeatureRange(const Inspector &i) = 0;
    virtual void visitNegation(const Inspector &i) = 0;
    virtual void visitConjunction(const Inspector &i) = 0;
    virtual void visitDisjunction(const Inspector &i) = 0;
    virtual void visitTrue(const Inspector &i) = 0;
    virtual void visitFalse(const Inspector &i) = 0;

protected:
    void visitChildren(const Inspector &i);

public:
    virtual ~PredicateSlimeVisitor() {}

    void visit(const Inspector &i);
};

}  // namespace document

