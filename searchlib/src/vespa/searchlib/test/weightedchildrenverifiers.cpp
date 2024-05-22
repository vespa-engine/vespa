// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "weightedchildrenverifiers.h"

using search::queryeval::SearchIterator;

namespace search::test {

WeightedChildrenVerifier::WeightedChildrenVerifier()
    : _weights(_num_children, 1)
{ }
WeightedChildrenVerifier::~WeightedChildrenVerifier() = default;


IteratorChildrenVerifier::IteratorChildrenVerifier()
    : WeightedChildrenVerifier(),
      _split_lists(_num_children)
{
    auto full_list = getExpectedDocIds();
    for (size_t i = 0; i < full_list.size(); ++i) {
        _split_lists[i % _num_children].push_back(full_list[i]);
    }
}
IteratorChildrenVerifier::~IteratorChildrenVerifier() = default;

SearchIterator::UP
IteratorChildrenVerifier::create(bool strict) const {
    (void) strict;
    std::vector<SearchIterator*> children;
    for (size_t i = 0; i < _num_children; ++i) {
        children.push_back(createIterator(_split_lists[i], true).release());
    }
    return create(children);
}

SearchIterator::UP
IteratorChildrenVerifier::create(const std::vector<SearchIterator*> &children) const {
    (void) children;
    return {};
}


DwwIteratorChildrenVerifier::DwwIteratorChildrenVerifier()
    : WeightedChildrenVerifier(),
      _helper()
{
    _helper.add_docs(getDocIdLimit());
    auto full_list = getExpectedDocIds();
    for (size_t i = 0; i < full_list.size(); ++i) {
        _helper.set_doc(full_list[i], i % _num_children, 1);
    }
}
DwwIteratorChildrenVerifier::~DwwIteratorChildrenVerifier() = default;

SearchIterator::UP
DwwIteratorChildrenVerifier::create(bool strict) const {
    (void) strict;
    std::vector<DocidWithWeightIterator> children;
    for (size_t i = 0; i < _num_children; ++i) {
        auto dict_entry = _helper.dww().lookup(vespalib::make_string("%zu", i).c_str(), _helper.dww().get_dictionary_snapshot());
        _helper.dww().create(dict_entry.posting_idx, children);
    }
    return create(std::move(children));
}
SearchIterator::UP
DwwIteratorChildrenVerifier::create(std::vector<DocidWithWeightIterator> &&) const  {
    return {};
}


}
