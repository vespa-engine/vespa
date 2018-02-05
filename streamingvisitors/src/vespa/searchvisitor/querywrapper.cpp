// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "querywrapper.h"

using namespace search;

namespace storage {

QueryWrapper::PhraseList::PhraseList(Query & query) :
    _phrases()
{
    QueryNodeRefList phrases;
    query.getPhrases(phrases);
    for (size_t i = 0; i < phrases.size(); ++i) {
        _phrases.push_back(static_cast<PhraseQueryNode *>(phrases[i]));
    }
}

PhraseQueryNode *
QueryWrapper::PhraseList::findPhrase(QueryTerm * term, size_t & index)
{
    for (size_t i = 0; i < _phrases.size(); ++i) {
        for (size_t j = 0; j < _phrases[i]->size(); ++j) {
            if ((*_phrases[i])[j].get() == term) {
                index = j;
                return _phrases[i];
            }
        }
    }
    return nullptr;
}

QueryWrapper::QueryWrapper(Query & query) :
    _phraseList(query),
    _termList()
{
    QueryTermList leafs;
    query.getLeafs(leafs);
    for (size_t i = 0; i < leafs.size(); ++i) {
        size_t index = 0;
        PhraseQueryNode * parent = _phraseList.findPhrase(leafs[i], index);
        _termList.push_back(Term(leafs[i], parent, index));
    }
}


} // namespace storage

