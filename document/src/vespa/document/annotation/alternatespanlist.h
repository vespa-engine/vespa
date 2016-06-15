// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "spanlist.h"
#include "spannode.h"
#include "spantreevisitor.h"
#include <memory>
#include <vector>

namespace document {
class AlternateSpanList : public SpanNode {
    struct Subtree {
        SpanList *span_list;
        double probability;
        Subtree() : span_list(0), probability(0.0) {}
    };
    std::vector<Subtree> _subtrees;

    void add(size_t index, std::unique_ptr<SpanNode> node);

public:
    typedef std::unique_ptr<AlternateSpanList> UP;

    ~AlternateSpanList();

    template <typename T>
    T &add(size_t index, std::unique_ptr<T> node) {
        T *n = node.get();
        add(index, std::unique_ptr<SpanNode>(std::move(node)));
        return *n;
    }

    void setSubtree(size_t index, std::unique_ptr<SpanList> subtree);
    void setProbability(size_t index, double probability);

    size_t getNumSubtrees() const { return _subtrees.size(); }
    SpanList &getSubtree(size_t index) const;
    double getProbability(size_t index) const;

    virtual void accept(SpanTreeVisitor &visitor) const { visitor.visit(*this); }
    virtual void print(
            std::ostream& out, bool verbose, const std::string& indent) const;
};

}  // namespace document

