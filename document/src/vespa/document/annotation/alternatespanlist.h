// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "spanlist.h"
#include "spannode.h"
#include <memory>
#include <vector>

namespace document {
class AlternateSpanList : public SpanNode {
    struct Subtree {
        SpanList *span_list;
        double probability;
        Subtree() noexcept : span_list(0), probability(0.0) {}
    };
    std::vector<Subtree> _subtrees;

    void addInternal(size_t index, std::unique_ptr<SpanNode> node);

public:
    typedef std::unique_ptr<AlternateSpanList> UP;
    typedef std::vector<Subtree>::const_iterator const_iterator;

    ~AlternateSpanList();

    template <typename T>
    T &add(size_t index, std::unique_ptr<T> node) {
        T *n = node.get();
        addInternal(index, std::unique_ptr<SpanNode>(std::move(node)));
        return *n;
    }

    void setSubtree(size_t index, std::unique_ptr<SpanList> subtree);
    void setProbability(size_t index, double probability);

    size_t getNumSubtrees() const { return _subtrees.size(); }
    SpanList &getSubtree(size_t index) const;
    double getProbability(size_t index) const;

    size_t size() const { return _subtrees.size(); }
    const_iterator begin() const { return _subtrees.begin(); }
    const_iterator end() const { return _subtrees.end(); }
    void accept(SpanTreeVisitor &visitor) const override;
};

}  // namespace document

