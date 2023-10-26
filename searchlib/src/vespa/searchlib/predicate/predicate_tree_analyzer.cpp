// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_tree_analyzer.h"
#include <vespa/document/predicate/predicate.h>
#include <algorithm>
#include <cmath>
#include <cassert>

using document::Predicate;
using std::map;
using std::min;
using std::string;
using vespalib::slime::Inspector;
using vespalib::Memory;

namespace search::predicate {

namespace {
long getType(const Inspector &in, bool negated) {
    long type = in[Predicate::NODE_TYPE].asLong();
    if (negated) {
        if (type == Predicate::TYPE_CONJUNCTION) {
            return Predicate::TYPE_DISJUNCTION;
        } else if (type == Predicate::TYPE_DISJUNCTION) {
            return Predicate::TYPE_CONJUNCTION;
        }
    }
    return type;
}

void createOrIncrease(map<string, int> &counts, const string &key) {
    auto it = counts.find(key);
    if (it == counts.end()) {
        counts.insert(make_pair(key, 1));
    } else {
        ++(it->second);
    }
}
}  // namespace

void PredicateTreeAnalyzer::traverseTree(const Inspector &in) {
    switch (getType(in, _negated)) {
    case Predicate::TYPE_NEGATION:
        assert(in[Predicate::CHILDREN].children() == 1);
        _negated = !_negated;
        traverseTree(in[Predicate::CHILDREN][0]);
        _negated = !_negated;
        return;
    case Predicate::TYPE_CONJUNCTION: {
        int crumb_size = _crumbs.size();
        int size = 0;
        for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
            _crumbs.setChild(i, 'a');
            traverseTree(in[Predicate::CHILDREN][i]);
            size += _size;
            _size_map.insert(make_pair(_crumbs.getCrumb(), _size));
            _crumbs.resize(crumb_size);
        }
        _size = size;
        return;
    }
    case Predicate::TYPE_DISJUNCTION: {
        int crumb_size = _crumbs.size();
        int size = 0;
        for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
            _crumbs.setChild(i, 'o');
            traverseTree(in[Predicate::CHILDREN][i]);
            size += _size;
            _crumbs.resize(crumb_size);
        }
        _size = size;
        return;
    }
    case Predicate::TYPE_FEATURE_SET:
        if (_negated) {
            _size = 2;
            _has_not = true;
        } else {
            _size = 1;
            Memory label_mem = in[Predicate::KEY].asString();
            string label(label_mem.data, label_mem.size);
            label.push_back('=');
            const size_t prefix_size = label.size();
            for (size_t i = 0; i < in[Predicate::SET].children(); ++i) {
                Memory value = in[Predicate::SET][i].asString();
                label.resize(prefix_size);
                label.append(value.data, value.size);
                createOrIncrease(_key_counts, label);
            }
        }
        return;
    case Predicate::TYPE_FEATURE_RANGE: {
        if (_negated) {
            _size = 2;
            _has_not = true;
        } else {
            _size = 1;
            string key = in[Predicate::KEY].asString().make_string();
            createOrIncrease(_key_counts, key);
        }
    }
    }  // switch
}

float PredicateTreeAnalyzer::findMinFeature(const Inspector &in) {
    float min_feature = 0.0f;
    switch (getType(in, _negated)) {
    case Predicate::TYPE_CONJUNCTION:  // sum of children
        for (size_t i = 0; i < in[Predicate::CHILDREN].children(); ++i) {
            min_feature += findMinFeature(in[Predicate::CHILDREN][i]);
        }
        return min_feature;
    case Predicate::TYPE_DISJUNCTION:  // min of children
        min_feature = findMinFeature(in[Predicate::CHILDREN][0]);
        for (size_t i = 1; i < in[Predicate::CHILDREN].children(); ++i) {
            min_feature = min(min_feature,
                              findMinFeature(in[Predicate::CHILDREN][i]));
        }
        return min_feature;
    case Predicate::TYPE_NEGATION:  // == child
        assert(in[Predicate::CHILDREN].children() == 1);
        _negated = !_negated;
        min_feature = findMinFeature(in[Predicate::CHILDREN][0]);
        _negated = !_negated;
        return min_feature;
    case Predicate::TYPE_FEATURE_SET: {
        if (_negated) {
            return 0.0f;
        }
        Memory label_mem = in[Predicate::KEY].asString();
        string label(label_mem.data, label_mem.size);
        label.push_back('=');
        const size_t prefix_size = label.size();
        min_feature = 1.0f;
        for (size_t i = 0; i < in[Predicate::SET].children(); ++i) {
            Memory value = in[Predicate::SET][i].asString();
            label.resize(prefix_size);
            label.append(value.data, value.size);
            auto it = _key_counts.find(label);
            assert(it != _key_counts.end());
            min_feature = min(min_feature, 1.0f / it->second);
        }
        return min_feature;
    }
    case Predicate::TYPE_FEATURE_RANGE: {
        if (_negated) {
            return 0.0f;
        }
        string key = in[Predicate::KEY].asString().make_string();
        auto it = _key_counts.find(key);
        assert(it != _key_counts.end());
        return 1.0f / it->second;
    }
    }  // switch
    return 0.0f;
}

PredicateTreeAnalyzer::PredicateTreeAnalyzer(const Inspector &in)
    : _has_not(false),
      _negated(false)
{
    traverseTree(in);
    _min_feature = static_cast<int>(std::ceil(float(findMinFeature(in)) + (_has_not? 1.0 : 0.0)));
}

PredicateTreeAnalyzer::~PredicateTreeAnalyzer() = default;

}
