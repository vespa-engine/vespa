// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "alternatespanlist.h"
#include "spantreevisitor.h"
#include <cassert>

using std::unique_ptr;

namespace document {
namespace {
template <typename T>
void ensureSize(size_t size, T &t) {
    if (size > t.size()) {
        t.resize(size);
    }
}
}  // namespace

void AlternateSpanList::addInternal(size_t index, unique_ptr<SpanNode> node) {
    ensureSize(index + 1, _subtrees);
    Subtree &subtree = _subtrees[index];
    if (!subtree.span_list) {
        subtree.span_list = new SpanList;
    }
    subtree.span_list->add(std::move(node));
}

AlternateSpanList::~AlternateSpanList() {
    for (size_t i = 0; i < _subtrees.size(); ++i) {
        delete _subtrees[i].span_list;
    }
}

void AlternateSpanList::setSubtree(size_t index, std::unique_ptr<SpanList> subtree) {
    ensureSize(index + 1, _subtrees);
    _subtrees[index].span_list = subtree.release();
}

void AlternateSpanList::setProbability(size_t index, double probability) {
    ensureSize(index + 1, _subtrees);
    _subtrees[index].probability = probability;
}

SpanList &AlternateSpanList::getSubtree(size_t index) const {
    assert(index < _subtrees.size());
    assert(_subtrees[index].span_list);
    return *_subtrees[index].span_list;
}

double AlternateSpanList::getProbability(size_t index) const {
    assert(index < _subtrees.size());
    return _subtrees[index].probability;
}

void AlternateSpanList::accept(SpanTreeVisitor &visitor) const {
    visitor.visit(*this);
}

}  // namespace document
