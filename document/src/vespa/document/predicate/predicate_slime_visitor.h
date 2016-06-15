// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {
namespace slime { class Inspector; }
}  // namespace vespalib

namespace document {

class PredicateSlimeVisitor {
    virtual void visitFeatureSet(const vespalib::slime::Inspector &i) = 0;
    virtual void visitFeatureRange(const vespalib::slime::Inspector &i) = 0;
    virtual void visitNegation(const vespalib::slime::Inspector &i) = 0;
    virtual void visitConjunction(const vespalib::slime::Inspector &i) = 0;
    virtual void visitDisjunction(const vespalib::slime::Inspector &i) = 0;
    virtual void visitTrue(const vespalib::slime::Inspector &i) = 0;
    virtual void visitFalse(const vespalib::slime::Inspector &i) = 0;

protected:
    void visitChildren(const vespalib::slime::Inspector &i);

public:
    virtual ~PredicateSlimeVisitor() {}

    void visit(const vespalib::slime::Inspector &i);
};

}  // namespace document

