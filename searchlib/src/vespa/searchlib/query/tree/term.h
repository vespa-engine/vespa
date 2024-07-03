// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "node.h"
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/stllike/string.h>

namespace search::query {

/**
 * This is a leaf in the Query tree. Sort of. Phrases are both terms
 * and intermediate nodes.
 */
class Term
{
    vespalib::string _view;
    int32_t          _id;
    Weight           _weight;
    bool             _ranked;
    bool             _position_data;
    bool             _prefix_match;

public:
    virtual ~Term() = 0;

    void setView(const vespalib::string & view) { _view = view; }
    void setRanked(bool ranked) noexcept { _ranked = ranked; }
    void setPositionData(bool position_data) noexcept { _position_data = position_data; }
    // Used for fuzzy prefix matching. Not to be confused with distinct Prefix query term type
    void set_prefix_match(bool prefix_match) noexcept { _prefix_match = prefix_match; }

    void setStateFrom(const Term& other);

    const vespalib::string & getView() const noexcept { return _view; }
    Weight getWeight() const noexcept { return _weight; }
    int32_t getId() const noexcept { return _id; }
    [[nodiscard]] bool isRanked() const noexcept { return _ranked; }
    [[nodiscard]] bool usePositionData() const noexcept { return _position_data; }
    [[nodiscard]] bool prefix_match() const noexcept { return _prefix_match; }

    static bool isPossibleRangeTerm(std::string_view term) noexcept {
        return (term[0] == '[' || term[0] == '<' || term[0] == '>');
    }
protected:
    Term(std::string_view view, int32_t id, Weight weight);
};

class TermNode : public Node, public Term {
protected:
    TermNode(std::string_view view, int32_t id, Weight weight) : Term(view, id, weight) {}
};
/**
 * Generic functionality for most of Term's derived classes.
 */
template <typename T>
class TermBase : public TermNode {
    T _term;

public:
    using Type = T;

    ~TermBase() override = 0;
    const T &getTerm() const { return _term; }

protected:
    TermBase(T term, std::string_view view, int32_t id, Weight weight);
};


template <typename T>
TermBase<T>::TermBase(T term, std::string_view view, int32_t id, Weight weight)
    : TermNode(view, id, weight),
       _term(std::move(term))
{}

template <typename T>
TermBase<T>::~TermBase() = default;

}
