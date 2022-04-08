// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "result.h"
#include <vector>
#include <vespa/document/fieldvalue/variablemap.h>

namespace document::select {

class ResultList : public Printable {
public:
    using VariableMap = fieldvalue::VariableMap;
    typedef std::pair<VariableMap, const Result*> ResultPair;
    typedef std::vector<ResultPair> Results;
    typedef Results::iterator iterator;
    typedef Results::const_iterator const_iterator;
    using reverse_iterator = Results::reverse_iterator;

    ResultList();
    ResultList(ResultList &&) noexcept;
    ResultList & operator = (ResultList &&) noexcept;
    ResultList(const ResultList &) = delete;
    ResultList & operator = (const ResultList &) = delete;
    ~ResultList();

    /**
       Creates a result list with one element with the given result type and no parameters.
    */
    explicit ResultList(const Result& result);

    void add(VariableMap variables, const Result& result);

    ResultList operator&&(const ResultList& other) const;
    ResultList operator||(const ResultList& other) const;
    ResultList operator!() &&;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    bool isEmpty() const { return _results.empty(); }

    const Result& combineResults() const;

    bool operator==(const ResultList& other) const;

    const Results& getResults() const { return _results; }
    const_iterator begin() const { return _results.begin(); }
    const_iterator end() const { return _results.end(); }
    reverse_iterator rbegin() { return _results.rbegin(); }
    reverse_iterator rend() { return _results.rend(); }

private:
    Results _results;
    static bool combineVariables(VariableMap & combination, const VariableMap& output, const VariableMap& input);
};

inline bool operator==(const ResultList& list, const Result& other) {
    return (list.combineResults() == other);
}

inline bool operator!=(const ResultList& list, const Result& other) {
    return (list.combineResults() != other);
}

}

