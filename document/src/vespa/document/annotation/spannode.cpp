// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spannode.h"
#include "spantreevisitor.h"
#include "alternatespanlist.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace document {

namespace {

class ToStringVisitor : public SpanTreeVisitor {
public:
    ToStringVisitor();
    ~ToStringVisitor();
    vespalib::stringref str() const { return _os.str(); }
private:
    vespalib::asciistream _os;
    vespalib::string _indent;

    void newline() {
        _os << "\n" << _indent;
    }
    void visitChildren(const SpanList & list) {
        for (const SpanNode * node : list) {
            newline();
            node->accept(*this);
        }
    }

    void visitChildren(const SimpleSpanList & list) {
        for (const Span & node : list) {
            newline();
            node.accept(*this);
        }
    }

    void visit(const Span & span) override {
        _os << "Span(" << span.from() << ", " << span.length() << ")";
    }

    void visit(const SpanList & list) override {
        _os << "SpanList(";
        if (list.size() > 1) {
            vespalib::string oldIndent(_indent);
            _indent += "  ";
            visitChildren(list);
            _indent = oldIndent;
            newline();
        } else {
            (*list.begin())->accept(*this);
        }
        _os << ")";
    }
    void visit(const SimpleSpanList & list) override {
        _os << "SimpleSpanList(";
        if (list.size() > 1) {
            vespalib::string oldIndent(_indent);
            _indent += "  ";
            visitChildren(list);
            _indent = oldIndent;
            newline();
        } else {
            visit(*list.begin());
        }
        _os << ")";
    }
    void visit(const AlternateSpanList & list) override {
        _os << "AlternateSpanList(";
        vespalib::string oldIndent(_indent);
        _indent += "  ";
        for (size_t i = 0; i < list.getNumSubtrees(); ++i) {
            newline();
            _os << "Probability " << list.getProbability(i) << " : ";
            visit(list.getSubtree(i));
        }
        _indent = oldIndent;
        newline();
        _os << ")";
    }
};

ToStringVisitor::ToStringVisitor() : _os(), _indent() { }
ToStringVisitor::~ToStringVisitor() { }

}

vespalib::string
SpanNode::toString() const {
    ToStringVisitor os;
    accept(os);
    return os.str();
}

std::ostream & operator << (std::ostream & os, const SpanNode & node) {
    return os << node.toString();
}

}
