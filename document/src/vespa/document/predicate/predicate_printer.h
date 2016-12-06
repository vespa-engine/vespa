// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "predicate_slime_visitor.h"
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
    class Slime;
    class asciistream;
}

namespace document {

class PredicatePrinter : PredicateSlimeVisitor {
    std::unique_ptr<vespalib::asciistream> _out;
    bool _negated;

    virtual void visitFeatureSet(const vespalib::slime::Inspector &i);
    virtual void visitFeatureRange(const vespalib::slime::Inspector &i);
    virtual void visitNegation(const vespalib::slime::Inspector &i);
    virtual void visitConjunction(const vespalib::slime::Inspector &i);
    virtual void visitDisjunction(const vespalib::slime::Inspector &i);
    virtual void visitTrue(const vespalib::slime::Inspector &i);
    virtual void visitFalse(const vespalib::slime::Inspector &i);

    vespalib::string str() const;

    PredicatePrinter();
    ~PredicatePrinter();
public:
    static vespalib::string print(const vespalib::Slime &slime);
};

}  // namespace document

