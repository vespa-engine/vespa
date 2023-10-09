// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    void visitFeatureSet(const Inspector &i) override;
    void visitFeatureRange(const Inspector &i) override;
    void visitNegation(const Inspector &i) override;
    void visitConjunction(const Inspector &i) override;
    void visitDisjunction(const Inspector &i) override;
    void visitTrue(const Inspector &i) override;
    void visitFalse(const Inspector &i) override;

    vespalib::string str() const;

    PredicatePrinter();
    ~PredicatePrinter();
public:
    static vespalib::string print(const vespalib::Slime &slime);
};

}  // namespace document

