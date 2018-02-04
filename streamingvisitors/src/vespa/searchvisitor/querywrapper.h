// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/query.h>
#include <vespa/searchlib/query/querynode.h>

namespace storage {

/**
 * This class wraps a query and adds extra information to the list of leaf terms.
 **/
class QueryWrapper
{
public:
    class PhraseList {
    private:
        std::vector<search::PhraseQueryNode *> _phrases;

    public:
        PhraseList(search::Query & query);
        search::PhraseQueryNode * findPhrase(search::QueryTerm * term, size_t & index);
    };

    class Term {
    private:
        search::QueryTerm       * _term;
        search::PhraseQueryNode * _parent;
        size_t                    _index;

    public:
        Term() :
            _term(nullptr),
            _parent(nullptr),
            _index(0)
        {
        }
        Term(search::QueryTerm * term, search::PhraseQueryNode * parent, size_t index) :
            _term(term),
            _parent(parent),
            _index(index)
        {
        }
        search::QueryTerm * getTerm() { return _term; }
        search::PhraseQueryNode * getParent() { return _parent; }
        size_t getIndex() const { return _index; }
        bool isPhraseTerm() const { return _parent != nullptr; }
        bool isFirstPhraseTerm() const { return isPhraseTerm() && getIndex() == 0; }
        size_t getPosAdjust() const { return _parent != nullptr ? _parent->width() - 1 : 0; }
    };

    typedef std::vector<Term> TermList;

private:
    PhraseList _phraseList;
    TermList   _termList;

public:
    QueryWrapper(search::Query & query);
    TermList & getTermList() { return _termList; }
    const TermList & getTermList() const { return _termList; }
};

} // namespace storage

