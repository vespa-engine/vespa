// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate.h"
#include "predicate_slime_builder.h"


using vespalib::Slime;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;

namespace document {

PredicateSlimeBuilder::PredicateSlimeBuilder()
    : _slime(new Slime),
      _cursor(&_slime->setObject()) {
}

PredicateSlimeBuilder &PredicateSlimeBuilder::feature(const std::string &key) {
    _cursor->setString(Predicate::KEY, key);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::value(const std::string &val) {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_SET);
    Cursor *arr = &(*_cursor)[Predicate::SET];
    if (!arr->valid()) {
        arr = &_cursor->setArray(Predicate::SET);
    }
    arr->addString(val);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::range(int64_t lower,
                                                    int64_t upper) {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_RANGE);
    _cursor->setLong(Predicate::RANGE_MIN, lower);
    _cursor->setLong(Predicate::RANGE_MAX, upper);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::greaterEqual(int64_t lower) {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_RANGE);
    _cursor->setLong(Predicate::RANGE_MIN, lower);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::lessEqual(int64_t upper) {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_RANGE);
    _cursor->setLong(Predicate::RANGE_MAX, upper);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::neg() {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_NEGATION);
    _cursor = &_cursor->setArray(Predicate::CHILDREN).addObject();
    return *this;
}

namespace {
template <typename InitList>
void intermediateNode(long type, InitList list, Cursor &cursor) {
    cursor.setLong(Predicate::NODE_TYPE, type);
    Cursor &arr = cursor.setArray(Predicate::CHILDREN);
    for (auto it = list.begin(); it != list.end(); ++it) {
        inject((*it)->get(), ArrayInserter(arr));
    }
}
}  // namespace

PredicateSlimeBuilder &PredicateSlimeBuilder::and_node(
        std::initializer_list<SlimeUP> list) {
    intermediateNode(Predicate::TYPE_CONJUNCTION, list, *_cursor);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::or_node(
        std::initializer_list<SlimeUP> list) {
    intermediateNode(Predicate::TYPE_DISJUNCTION, list, *_cursor);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::and_node(SlimeUP s1,
                                                       SlimeUP s2) {
    return and_node({std::move(s1), std::move(s2)});
}

PredicateSlimeBuilder &PredicateSlimeBuilder::or_node(SlimeUP s1, SlimeUP s2) {
    return or_node({std::move(s1), std::move(s2)});
}

PredicateSlimeBuilder &PredicateSlimeBuilder::true_predicate() {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_TRUE);
    return *this;
}

PredicateSlimeBuilder &PredicateSlimeBuilder::false_predicate() {
    _cursor->setLong(Predicate::NODE_TYPE, Predicate::TYPE_FALSE);
    return *this;
}

std::unique_ptr<vespalib::Slime> PredicateSlimeBuilder::build() {
    std::unique_ptr<Slime> s = std::move(_slime);
    _slime.reset(new Slime);
    _cursor = &_slime->setObject();
    return s;
}

namespace predicate_slime_builder {

SlimeUP featureSet(const std::string &key,
                   const std::initializer_list<std::string> &values) {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_SET);
    cursor.setString(Predicate::KEY, key);
    Cursor &arr = cursor.setArray(Predicate::SET);
    for (const auto &value : values) {
        arr.addString(value);
    }
    return slime;
}

SlimeUP emptyRange(const std::string &key) {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_RANGE);
    cursor.setString(Predicate::KEY, key);
    return slime;
}

SlimeUP featureRange(const std::string &key, int64_t lower, int64_t upper) {
    SlimeUP slime = emptyRange(key);
    Cursor &cursor = slime->get();
    cursor.setLong(Predicate::RANGE_MIN, lower);
    cursor.setLong(Predicate::RANGE_MAX, upper);
    return slime;
}

SlimeUP greaterEqual(const std::string &key, int64_t lower) {
    SlimeUP slime = emptyRange(key);
    Cursor &cursor = slime->get();
    cursor.setLong(Predicate::RANGE_MIN, lower);
    return slime;
}

SlimeUP lessEqual(const std::string &key, int64_t upper) {
    SlimeUP slime = emptyRange(key);
    Cursor &cursor = slime->get();
    cursor.setLong(Predicate::RANGE_MAX, upper);
    return slime;
}

SlimeUP neg(SlimeUP child) {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_NEGATION);
    Cursor &arr = cursor.setArray(Predicate::CHILDREN);
    inject(child->get(), ArrayInserter(arr));
    return slime;
}

SlimeUP andNode(const std::initializer_list<SlimeUP> &children) {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_CONJUNCTION);
    Cursor &arr = cursor.setArray(Predicate::CHILDREN);
    for (const auto &child : children) {
        inject(child->get(), ArrayInserter(arr));
    }
    return slime;
}

SlimeUP orNode(const std::initializer_list<SlimeUP> &children) {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_DISJUNCTION);
    Cursor &arr = cursor.setArray(Predicate::CHILDREN);
    for (const auto &child : children) {
        inject(child->get(), ArrayInserter(arr));
    }
    return slime;
}

SlimeUP truePredicate() {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_TRUE);
    return slime;
}

SlimeUP falsePredicate() {
    SlimeUP slime(new Slime);
    Cursor &cursor = slime->setObject();
    cursor.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FALSE);
    return slime;
}

}  // namespace predicate_slime_builder
}  // namespace document
