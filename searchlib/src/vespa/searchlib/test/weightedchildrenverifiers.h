// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "searchiteratorverifier.h"
#include "document_weight_attribute_helper.h"

namespace search::test {

class WeightedChildrenVerifier : public SearchIteratorVerifier {
public:
    WeightedChildrenVerifier();
    ~WeightedChildrenVerifier() override;
protected:
    static constexpr size_t _num_children = 7;
    mutable fef::TermFieldMatchData _tfmd;
    std::vector<int32_t> _weights;
};

class IteratorChildrenVerifier : public WeightedChildrenVerifier {
public:
    IteratorChildrenVerifier();
    ~IteratorChildrenVerifier() override;
    SearchIterator::UP create(bool strict) const override;
protected:
    virtual SearchIterator::UP create(const std::vector<SearchIterator*> &children) const;
    std::vector<DocIds> _split_lists;
};

class DwwIteratorChildrenVerifier : public WeightedChildrenVerifier {
public:
    DwwIteratorChildrenVerifier();
    ~DwwIteratorChildrenVerifier() override;
    SearchIterator::UP create(bool strict) const override;
protected:
    virtual SearchIterator::UP create(std::vector<DocidWithWeightIterator> &&) const;
    DocumentWeightAttributeHelper _helper;
};

}
