// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/streaming/query.h>
#include <vespa/searchlib/query/streaming/querynode.h>

namespace streaming {

/**
 * This class wraps a query and adds extra information to the list of leaf terms.
 **/
class QueryWrapper
{
public:
    class PhraseList {
    private:
        std::vector<search::streaming::PhraseQueryNode *> _phrases;

    public:
        PhraseList(search::streaming::Query & query);
        search::streaming::PhraseQueryNode * findPhrase(search::streaming::QueryTerm * term, size_t & index);
    };

    class Term {
    private:
        search::streaming::QueryTerm       * _term;
        search::streaming::PhraseQueryNode * _parent;
        size_t                    _index;

    public:
        Term() :
            _term(nullptr),
            _parent(nullptr),
            _index(0)
        {
        }
        Term(search::streaming::QueryTerm * term, search::streaming::PhraseQueryNode * parent, size_t index) :
            _term(term),
            _parent(parent),
            _index(index)
        {
        }
        search::streaming::QueryTerm * getTerm() { return _term; }
        search::streaming::PhraseQueryNode * getParent() { return _parent; }
        size_t getIndex() const { return _index; }
        bool isPhraseTerm() const { return _parent != nullptr; }
        bool isFirstPhraseTerm() const { return isPhraseTerm() && getIndex() == 0; }
        size_t getPosAdjust() const { return _parent != nullptr ? _parent->width() - 1 : 0; }
        bool isGeoPosTerm() const { return (_term != nullptr) && _term->isGeoLoc(); }
    };

    typedef std::vector<Term> TermList;

private:
    PhraseList _phraseList;
    TermList   _termList;

public:
    QueryWrapper(search::streaming::Query & query);
    ~QueryWrapper();
    TermList & getTermList() { return _termList; }
    const TermList & getTermList() const { return _termList; }
};

} // namespace streaming

