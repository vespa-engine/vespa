// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiteratorverifier.h"
#include "document_weight_attribute_helper.h"

namespace search::test {

class WeightedChildrenVerifier : public SearchIteratorVerifier {
public:
    WeightedChildrenVerifier()
        : _weights(_num_children, 1)
    { }
    ~WeightedChildrenVerifier() override {}

protected:
    static constexpr size_t _num_children = 7;
    mutable fef::TermFieldMatchData _tfmd;
    std::vector<int32_t> _weights;
};

class IteratorChildrenVerifier : public WeightedChildrenVerifier {
public:
    IteratorChildrenVerifier()
        : WeightedChildrenVerifier(),
          _split_lists(_num_children)
    {
        auto full_list = getExpectedDocIds();
        for (size_t i = 0; i < full_list.size(); ++i) {
            _split_lists[i % _num_children].push_back(full_list[i]);
        }
    }
    ~IteratorChildrenVerifier() override { }
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        std::vector<SearchIterator*> children;
        for (size_t i = 0; i < _num_children; ++i) {
            children.push_back(createIterator(_split_lists[i], true).release());
        }
        return create(children);
    }
protected:
    virtual SearchIterator::UP create(const std::vector<SearchIterator*> &children) const {
        (void) children;
        return SearchIterator::UP();
    }
    std::vector<DocIds> _split_lists;
};

class DwaIteratorChildrenVerifier : public WeightedChildrenVerifier {
public:
    DwaIteratorChildrenVerifier() :
        WeightedChildrenVerifier(),
        _helper()
    {
        _helper.add_docs(getDocIdLimit());
        auto full_list = getExpectedDocIds();
        for (size_t i = 0; i < full_list.size(); ++i) {
            _helper.set_doc(full_list[i], i % _num_children, 1);
        }
    }
    ~DwaIteratorChildrenVerifier() override {}
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        std::vector<DocumentWeightIterator> children;
        for (size_t i = 0; i < _num_children; ++i) {
            auto dict_entry = _helper.dwa().lookup(vespalib::make_string("%zu", i).c_str(), _helper.dwa().get_dictionary_snapshot());
            _helper.dwa().create(dict_entry.posting_idx, children);
        }
        return create(std::move(children));
    }
protected:
    virtual SearchIterator::UP create(std::vector<DocumentWeightIterator> &&) const  {
        return {};
    }
    DocumentWeightAttributeHelper _helper;
};

}
