// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate.h"
#include "predicate_printer.h"
#include <vespa/document/util/stringutil.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/asciistream.h>

using vespalib::Slime;
using vespalib::slime::Inspector;

namespace document {

namespace {
void printEscapedString(vespalib::asciistream &out, const Inspector &in) {
    out << "'" << StringUtil::escape(in.asString().make_string(), '\'') << "'";
}
}  // namespace

vespalib::string
PredicatePrinter::str() const {
    return _out->str();
}

PredicatePrinter::PredicatePrinter() : _out(new vespalib::asciistream()), _negated(false) {}
PredicatePrinter::~PredicatePrinter() { }


void PredicatePrinter::visitFeatureSet(const Inspector &in) {
    printEscapedString(*_out, in[Predicate::KEY]);
    if (_negated) {
        *_out << " not";
    }
    *_out << " in [";
    for (size_t i = 0; i < in[Predicate::SET].entries(); ++i) {
        if (i) {
            *_out << ",";
        }
        printEscapedString(*_out, in[Predicate::SET][i]);
    }
    *_out << "]";
}

void PredicatePrinter::visitFeatureRange(const Inspector &in) {
    printEscapedString(*_out, in[Predicate::KEY]);
    if (_negated) {
        *_out << " not";
    }
    bool has_min = in[Predicate::RANGE_MIN].valid();
    bool has_max = in[Predicate::RANGE_MAX].valid();
    *_out << " in [";
    if (has_min) *_out << in[Predicate::RANGE_MIN].asLong();
    *_out << "..";
    if (has_max) *_out << in[Predicate::RANGE_MAX].asLong();
    *_out << "]";
}

void PredicatePrinter::visitNegation(const Inspector &in) {
    bool n = _negated;
    _negated = !_negated;
    visitChildren(in);
    _negated = n;
}

void PredicatePrinter::visitConjunction(const Inspector &in) {
    if (_negated)
        *_out << "not ";
    _negated = false;
    *_out << "(";
    for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
        if (i) {
            *_out << " and ";
        }
        visit(in[Predicate::CHILDREN][i]);
    }
    *_out << ")";
}

void PredicatePrinter::visitDisjunction(const Inspector &in) {
    if (_negated)
        *_out << "not ";
    _negated = false;
    *_out << "(";
    for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
        if (i) {
            *_out << " or ";
        }
        visit(in[Predicate::CHILDREN][i]);
    }
    *_out << ")";
}

void PredicatePrinter::visitTrue(const Inspector &) {
    *_out << "true";
}

void PredicatePrinter::visitFalse(const Inspector &) {
    *_out << "false";
}

vespalib::string PredicatePrinter::print(const Slime &slime) {
    PredicatePrinter printer;
    printer.visit(slime.get());
    return printer.str();
}

}  // namespace document
