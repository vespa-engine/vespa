// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <set>
#include <climits>

using std::mismatch;
using std::pair;
using std::set;
using std::string;
using std::vector;
using vespalib::Slime;
using namespace vespalib::slime;

namespace document {

const string Predicate::NODE_TYPE("type");
const string Predicate::KEY("key");
const string Predicate::SET("feature_set");
const string Predicate::RANGE_MIN("range_min");
const string Predicate::RANGE_MAX("range_max");
const string Predicate::CHILDREN("children");
const string Predicate::HASHED_PARTITIONS("hashed_partitions");
const string Predicate::HASHED_EDGE_PARTITIONS("hashed_edge_partitions");
const string Predicate::HASH("hash");
const string Predicate::PAYLOAD("payload");
const string Predicate::VALUE("value");
const string Predicate::UPPER_BOUND("upper_bound");

namespace {
int compareSets(const Inspector &set1, const Inspector &set2) {
    if (set1.entries() < set2.entries()) {
        return -1;
    } else if (set1.entries() > set2.entries()) {
        return 1;
    }
    set<string> s1, s2;
    for (size_t i = 0; i < set1.entries(); ++i) {
        s1.insert(set1[i].asString().make_string());
        s2.insert(set2[i].asString().make_string());
    }
    pair<set<string>::iterator, set<string>::iterator> r =
        mismatch(s1.begin(), s1.end(), s2.begin());
    if (r.first == s1.end()) { return 0; }
    if (*r.first < *r.second) { return -1; }
    return 1;
}

int compareLongs(const Inspector &long1, const Inspector &long2) {
    if (long1.valid() && !long2.valid()) { return -1; }
    if (!long1.valid() && long2.valid()) { return 1; }
    if (long1.asLong() < long2.asLong()) { return -1; }
    if (long1.asLong() > long2.asLong()) { return 1; }
    return 0;
}

int compareNodes(const Inspector &n1, const Inspector &n2) {
    int ret = compareLongs(n1[Predicate::NODE_TYPE], n2[Predicate::NODE_TYPE]);
    if (ret) { return ret; }
    if (n1[Predicate::CHILDREN].valid()) {
        for (size_t i = 0; i < n1[Predicate::CHILDREN].entries(); ++i) {
            int r = compareNodes(n1[Predicate::CHILDREN][i],
                                 n2[Predicate::CHILDREN][i]);
            if (r) { return r; }
        }
        return 0;
    } else {
        string key1 = n1[Predicate::KEY].asString().make_string();
        string key2 = n2[Predicate::KEY].asString().make_string();
        if (key1 < key2) { return -1; }
        if (key1 > key2) { return 1; }
        if (n1[Predicate::SET].valid()) {
            return compareSets(n1[Predicate::SET], n2[Predicate::SET]);
        } else {
            int r = compareLongs(n1[Predicate::RANGE_MIN],
                                 n2[Predicate::RANGE_MIN]);
            if (r) { return r; }
            return compareLongs(n1[Predicate::RANGE_MAX],
                                n2[Predicate::RANGE_MAX]);
        }
    }
}
}  // namespace

int Predicate::compare(const Slime &s1, const Slime &s2) {
    return compareNodes(s1.get(), s2.get());
}

namespace {
template <typename InsertIt>
class InsertFromArray : public ArrayTraverser {
    InsertIt _it;
public:
    InsertFromArray(InsertIt it) : _it(it) {}
    ArrayTraverser &ref() { return *this; }
    void entry(size_t, const Inspector &inspector) override {
        *_it++ = inspector.asString().make_string();
    }
};

template <typename InsertIt>
InsertFromArray<InsertIt> make_insert_from_array(InsertIt it) {
    return InsertFromArray<InsertIt>(it);
}

int64_t defaultUnlessDefined(const Inspector &i, int64_t default_value) {
    if (i.valid()) {
        return i.asLong();
    }
    return default_value;
}
}  // namespace


FeatureBase::FeatureBase(const Inspector &inspector)
    : _key(inspector[Predicate::KEY].asString().make_string()) {
}


FeatureSet::FeatureSet(const Inspector &inspector)
    : FeatureBase(inspector), _features() {
    inspector[Predicate::SET].traverse(
            make_insert_from_array(back_inserter(_features)).ref());
}

FeatureSet::~FeatureSet() {}

FeatureRange::FeatureRange(const Inspector &inspector)
    : FeatureBase(inspector),
      _min(defaultUnlessDefined(inspector[Predicate::RANGE_MIN], LLONG_MIN)),
      _max(defaultUnlessDefined(inspector[Predicate::RANGE_MAX], LLONG_MAX)),
      _has_min(inspector[Predicate::RANGE_MIN].valid()),
      _has_max(inspector[Predicate::RANGE_MAX].valid()) {
}

}  // namespace document
