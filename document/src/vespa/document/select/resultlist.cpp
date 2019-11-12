// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultlist.h"
#include <vespa/vespalib/stllike/hash_set.h>
#include <ostream>

namespace document::select {

ResultList::ResultList() = default;

ResultList::~ResultList() = default;

ResultList::ResultList(const Result& result) {
    add(VariableMap(), result);
}

void
ResultList::add(const fieldvalue::VariableMap& variables, const Result& result)
{
    _results.push_back(ResultPair(variables, &result));
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
ResultList::combineVariables(
        fieldvalue::VariableMap& output,
        const fieldvalue::VariableMap& input) const
{
    // First, verify that all variables are overlapping
    for (const auto & ovar : output) {
        auto found(input.find(ovar.first));

        if (found != input.end()) {
            if (!(found->second == ovar.second)) {
                return false;
            }
        }
    }

    for (const auto & ivar : input) {
        auto found(output.find(ivar.first));
        if (found != output.end()) {
            if (!(found->second == ivar.second)) {
                return false;
            }
        }
    }
    // Ok, variables are overlapping. Add all variables from input to output.
    for (const auto & ivar : input) {
        output[ivar.first] = ivar.second;
    }

    return true;
}

ResultList
ResultList::operator&&(const ResultList& other) const
{
    ResultList results;

    // TODO: optimize
    vespalib::hash_set<uint32_t> resultForNoVariables;
    for (const auto & it : _results) {
        for (const auto & it2 : other._results) {
            fieldvalue::VariableMap vars = it.first;

            if (combineVariables(vars, it2.first)) {
                const Result & result = *it.second && *it2.second;
                if (vars.empty()) {
                    resultForNoVariables.insert(result.toEnum());
                } else {
                    results.add(vars, result);
                }
            }
        }
    }
    for (uint32_t result : resultForNoVariables) {
        results.add(fieldvalue::VariableMap(), Result::fromEnum(result));
    }

    return results;
}

ResultList
ResultList::operator||(const ResultList& other) const
{
    ResultList results;

    // TODO: optimize
    vespalib::hash_set<uint32_t> resultForNoVariables;
    for (const auto & it : _results) {
        for (const auto & it2 : other._results) {
            fieldvalue::VariableMap vars = it.first;
            if (combineVariables(vars, it2.first)) {
                const Result & result = *it.second || *it2.second;
                if (vars.empty()) {
                    resultForNoVariables.insert(result.toEnum());
                } else {
                    results.add(vars, result);
                }
            }
        }
    }
    for (uint32_t result : resultForNoVariables) {
        results.add(fieldvalue::VariableMap(), Result::fromEnum(result));
    }

    return results;
}

ResultList
ResultList::operator!() const {
    ResultList result;

    for (const auto & it : _results) {
        result.add(it.first, !*it.second);
    }

    return result;
}

bool ResultList::operator==(const ResultList& other) const {
    return (combineResultsLocal(_results) == combineResultsLocal(other._results));
}

}
