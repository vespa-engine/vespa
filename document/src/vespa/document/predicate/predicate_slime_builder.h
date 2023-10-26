// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/slime.h>
#include <string>

namespace document {

class PredicateSlimeBuilder {
    std::unique_ptr<vespalib::Slime> _slime;
    vespalib::slime::Cursor *_cursor;

    using SlimeUP = std::unique_ptr<vespalib::Slime>;

public:
    PredicateSlimeBuilder();

    PredicateSlimeBuilder &feature(const std::string &key);
    PredicateSlimeBuilder &value(const std::string &key);
    PredicateSlimeBuilder &range(int64_t lower, int64_t upper);
    PredicateSlimeBuilder &greaterEqual(int64_t lower);
    PredicateSlimeBuilder &lessEqual(int64_t upper);
    PredicateSlimeBuilder &neg();
    PredicateSlimeBuilder &and_node(std::initializer_list<SlimeUP> list);
    PredicateSlimeBuilder &or_node(std::initializer_list<SlimeUP> list);

    PredicateSlimeBuilder &and_node(SlimeUP s1, SlimeUP s2);
    PredicateSlimeBuilder &or_node(SlimeUP s1, SlimeUP s2);
    PredicateSlimeBuilder &true_predicate();
    PredicateSlimeBuilder &false_predicate();
    SlimeUP build();

    // for converting builders to slime objects in initializer lists.
    operator SlimeUP() { return build(); }
};

namespace predicate_slime_builder {

using SlimeUP = std::unique_ptr<vespalib::Slime>;

SlimeUP featureSet(const std::string &key,
                   const std::initializer_list<std::string> &values);
SlimeUP featureRange(const std::string &key, int64_t lower, int64_t upper);
SlimeUP greaterEqual(const std::string &key, int64_t lower);
SlimeUP lessEqual(const std::string &key, int64_t upper);
SlimeUP emptyRange(const std::string &key);
SlimeUP neg(SlimeUP child);
SlimeUP andNode(const std::initializer_list<SlimeUP> &children);
SlimeUP orNode(const std::initializer_list<SlimeUP> &children);
SlimeUP truePredicate();
SlimeUP falsePredicate();

}  // namespace predicate_slime_builder

}  // namespace document

