// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>
#include <vector>

namespace vespalib {
class Slime;
namespace slime { struct Inspector; }
}  // namespace vespalib

namespace document {

struct Predicate {
    static const std::string NODE_TYPE;
    static const std::string KEY;
    static const std::string SET;
    static const std::string RANGE_MIN;
    static const std::string RANGE_MAX;
    static const std::string CHILDREN;
    static const std::string HASHED_PARTITIONS;
    static const std::string HASHED_EDGE_PARTITIONS;
    static const std::string HASH;
    static const std::string PAYLOAD;
    static const std::string VALUE;
    static const std::string UPPER_BOUND;

    static const int TYPE_CONJUNCTION = 1;
    static const int TYPE_DISJUNCTION = 2;
    static const int TYPE_NEGATION = 3;
    static const int TYPE_FEATURE_SET = 4;
    static const int TYPE_FEATURE_RANGE = 5;
    static const int TYPE_TRUE = 6;
    static const int TYPE_FALSE = 7;

    static int compare(const vespalib::Slime &s1, const vespalib::Slime &s2);
};

struct PredicateNode {
    using UP = std::unique_ptr<PredicateNode>;

    virtual ~PredicateNode() {}
};

class FeatureBase : public PredicateNode {
    const std::string _key;
public:
    FeatureBase(const vespalib::slime::Inspector &inspector);

    std::string getKey() const { return _key; }
};


class FeatureSet : public FeatureBase {
    std::vector<std::string> _features;

public:
    FeatureSet(const vespalib::slime::Inspector &inspector);
    ~FeatureSet();

    size_t getSize() const { return _features.size(); }
    std::string operator[](size_t i) const { return _features[i]; }
};


class FeatureRange : public FeatureBase {
    const long _min;
    const long _max;
    const bool _has_min;
    const bool _has_max;
public:
    FeatureRange(const vespalib::slime::Inspector &inspector);

    long getMin() const { return _min; }
    long getMax() const { return _max; }
    bool hasMin() const { return _has_min; }
    bool hasMax() const { return _has_max; }
};


class Negation : public PredicateNode {
    PredicateNode::UP _child;

public:
    Negation(PredicateNode::UP child) : _child(std::move(child)) {}

    PredicateNode& getChild() const { return *_child; }
};


class IntermediatePredicateNode : public PredicateNode {
    std::vector<PredicateNode *> _children;

public:
    IntermediatePredicateNode(const std::vector<PredicateNode *> children)
        : _children(children) {}
    ~IntermediatePredicateNode() {
        for (size_t i = 0; i < _children.size(); ++i)
            delete _children[i];
    }

    size_t getSize() const { return _children.size(); }
    PredicateNode *operator[](size_t i) const { return _children[i]; }
};


struct Conjunction : IntermediatePredicateNode {
    Conjunction(const std::vector<PredicateNode *> children)
        : IntermediatePredicateNode(children) {}
};

struct Disjunction : IntermediatePredicateNode {
    Disjunction(const std::vector<PredicateNode *> children)
        : IntermediatePredicateNode(children) {}
};

struct TruePredicate : PredicateNode {};
struct FalsePredicate : PredicateNode {};

}  // namespace document

