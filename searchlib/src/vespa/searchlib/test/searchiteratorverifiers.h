#pragma once

#include "searchiteratorverifier.h"
#include "document_weight_attribute_helper.h"

namespace search {
namespace test {

class Verifier : public SearchIteratorVerifier {
public:
    Verifier() :
        _weights(_num_children, 1)
    { }

protected:
    static constexpr size_t _num_children = 7;
    mutable fef::TermFieldMatchData _tfmd;
    std::vector<int32_t> _weights;
};

class IteratorChildrenVerifier : public Verifier {
public:
    IteratorChildrenVerifier() :
        Verifier(),
        _split_lists(_num_children)
    {
        auto full_list = getExpectedDocIds();
        for (size_t i = 0; i < full_list.size(); ++i) {
            _split_lists[i % _num_children].push_back(full_list[i]);
        }
    }
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        std::vector<SearchIterator*> children;
        for (size_t i = 0; i < _num_children; ++i) {
            children.push_back(createIterator(_split_lists[i], true).release());
        }
        return create(children);
    }
protected:
    virtual SearchIterator::UP create(const std::vector<SearchIterator*> &children) const = 0;
    std::vector<DocIds> _split_lists;
};

class WeightIteratorChildrenVerifier : public Verifier {
public:
    WeightIteratorChildrenVerifier() :
        Verifier(),
        _helper()
    {
        _helper.add_docs(getDocIdLimit());
        auto full_list = getExpectedDocIds();
        for (size_t i = 0; i < full_list.size(); ++i) {
            _helper.set_doc(full_list[i], i % _num_children, 1);
        }
    }
    SearchIterator::UP create(bool strict) const override {
        (void) strict;
        std::vector<DocumentWeightIterator> children;
        for (size_t i = 0; i < _num_children; ++i) {
            auto dict_entry = _helper.dwa().lookup(vespalib::make_string("%zu", i).c_str());
            _helper.dwa().create(dict_entry.posting_idx, children);
        }
        return create(std::move(children));
    }
private:
    virtual SearchIterator::UP create(std::vector<DocumentWeightIterator> && children) const = 0;
    DocumentWeightAttributeHelper _helper;
};

}
}
