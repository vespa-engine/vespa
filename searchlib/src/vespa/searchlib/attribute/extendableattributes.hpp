// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class search::SearchVisitor
 *
 * @brief Visitor that applies a search query to visitor data and converts them to a SearchResultCommand
 */
#pragma once

#include "extendableattributes.h"
#include "attrvector.hpp"

namespace search {

template <typename T>
std::unique_ptr<attribute::SearchContext>
SingleExtAttribute<T>::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const
{
    (void) term;
    (void) params;
    return {};
}

template <typename T>
SingleExtAttribute<T>::SingleExtAttribute(const vespalib::string &name)
    : Super(name, Config(BasicType::fromType(T()), attribute::CollectionType::SINGLE))
{}

template <typename T>
bool
SingleExtAttribute<T>::addDoc(typename Super::DocId &docId) {
    docId = this->_data.size();
    this->_data.push_back(attribute::getUndefined<T>());
    this->incNumDocs();
    this->setCommittedDocIdLimit(this->getNumDocs());
    return true;
}
template <typename T>
bool
SingleExtAttribute<T>::add(typename AddValueType<T>::Type v, int32_t ) {
    this->_data.back() = v;
    return true;
}
template <typename T>
void
SingleExtAttribute<T>::onAddDocs(typename Super::DocId lidLimit) {
    this->_data.reserve(lidLimit);
}

template <typename T>
MultiExtAttribute<T>::MultiExtAttribute(const vespalib::string &name, const attribute::CollectionType &ctype)
    : Super(name, Config(BasicType::fromType(T()), ctype))
{ }
template <typename T>
std::unique_ptr<attribute::SearchContext>
MultiExtAttribute<T>::getSearch(QueryTermSimpleUP term, const attribute::SearchContextParams & params) const
{
    (void) term;
    (void) params;
    return {};
}

template <typename T>
MultiExtAttribute<T>::MultiExtAttribute(const vespalib::string &name)
    : Super(name, Config(BasicType::fromType(static_cast<T>(0)), attribute::CollectionType::ARRAY))
{}

template <typename T>
bool
MultiExtAttribute<T>::addDoc(typename Super::DocId &docId) {
    docId = this->_idx.size() - 1;
    this->_idx.push_back(this->_idx.back());
    this->incNumDocs();
    this->setCommittedDocIdLimit(this->getNumDocs());
    return true;
}
template <typename T>
bool
MultiExtAttribute<T>::add(typename AddValueType<T>::Type v, int32_t) {
    this->_data.push_back(v);
    std::vector<uint32_t> &idx = this->_idx;
    idx.back()++;
    this->checkSetMaxValueCount(idx.back() - idx[idx.size() - 2]);
    return true;
}
template <typename T>
void
MultiExtAttribute<T>::onAddDocs(uint32_t lidLimit) {
    this->_data.reserve(lidLimit);
}

template <typename T>
MultiExtAttribute<T>::~MultiExtAttribute() = default;

template <typename B>
WeightedSetExtAttributeBase<B>::WeightedSetExtAttributeBase(const vespalib::string & name)
    : B(name, attribute::CollectionType::WSET),
      _weights()
{}

template <typename B>
WeightedSetExtAttributeBase<B>::~WeightedSetExtAttributeBase() = default;

template <typename B>
void
WeightedSetExtAttributeBase<B>::addWeight(int32_t w) {
    _weights.push_back(w);
}

}  // namespace search

