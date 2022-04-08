// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultlist.h"
#include <bitset>
#include <ostream>

namespace document::select {

ResultList::ResultList() = default;
ResultList::ResultList(ResultList &&) noexcept = default;
ResultList & ResultList::operator = (ResultList &&) noexcept = default;
ResultList::~ResultList() = default;

ResultList::ResultList(const Result& result) {
    add(VariableMap(), result);
}

void
ResultList::add(VariableMap variables, const Result& result)
{
    _results.emplace_back(std::move(variables), &result);
}

void
ResultList::print(std::ostream& out, bool, const std::string&) const
{
    out << "ResultList(";
    for (uint32_t i = 0; i < _results.size(); i++) {
        if (!_results[i].first.empty()) {
            out << _results[i].first.toString() << " => ";
        }
        out << _results[i].second->toString() << " ";
    }
    out << ")";
}

namespace {

const Result &
combineResultsLocal(const ResultList::Results & results)
{
    if (results.empty()) {
        return Result::False;
    }

    bool foundFalse = false;

    for (const auto & it : results) {
        if (*it.second == Result::True) {
            return Result::True;
        } else if (*it.second == Result::False) {
            foundFalse = true;
        }
    }

    return (foundFalse) ? Result::False : Result::Invalid;
}

}

const Result&
ResultList::combineResults() const {
    return combineResultsLocal(_results);
}

bool
ResultList::combineVariables(VariableMap& combination, const VariableMap& a, const VariableMap& b)
{
    // First, verify that all variables are overlapping
    for (const auto & ovar : a) {
        auto found(b.find(ovar.first));

        if (found != b.end()) {
            if (!(found->second == ovar.second)) {
                return false;
            }
        }
    }

    for (const auto & ivar : b) {
        auto found(a.find(ivar.first));
        if (found != a.end()) {
            if (!(found->second == ivar.second)) {
                return false;
            }
        }
    }
    // Ok, variables are overlapping. Add all variables from input to output.
    for (const auto & var : a) {
        combination[var.first] = var.second;
    }
    for (const auto & var : b) {
        combination[var.first] = var.second;
    }

    return true;
}

ResultList
ResultList::operator&&(const ResultList& other) const
{
    ResultList results;

    std::bitset<3> resultForNoVariables;
    for (const auto & it : _results) {
        for (const auto & it2 : other._results) {
            VariableMap vars;
            if ( combineVariables(vars, it.first, it2.first) ) {
                const Result & result = *it.second && *it2.second;
                if (vars.empty()) {
                    resultForNoVariables.set(result.toEnum());
                } else {
                    results.add(std::move(vars), result);
                }
            }
        }
    }
    for (uint32_t i(0); i < resultForNoVariables.size(); i++) {
        if (resultForNoVariables[i]) {
            results.add(VariableMap(), Result::fromEnum(i));
        }
    }

    return results;
}

ResultList
ResultList::operator||(const ResultList& other) const
{
    ResultList results;

    std::bitset<3> resultForNoVariables;
    for (const auto & it : _results) {
        for (const auto & it2 : other._results) {
            VariableMap vars;
            if (combineVariables(vars, it.first, it2.first)) {
                const Result & result = *it.second || *it2.second;
                if (vars.empty()) {
                    resultForNoVariables.set(result.toEnum());
                } else {
                    results.add(std::move(vars), result);
                }
            }
        }
    }
    for (uint32_t i(0); i < resultForNoVariables.size(); i++) {
        if (resultForNoVariables[i]) {
            results.add(VariableMap(), Result::fromEnum(i));
        }
    }

    return results;
}

ResultList
ResultList::operator!() && {
    ResultList result;

    for (auto & it : _results) {
        result.add(std::move(it.first), !*it.second);
    }

    return result;
}

bool ResultList::operator==(const ResultList& other) const {
    return (combineResultsLocal(_results) == combineResultsLocal(other._results));
}

}
