// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "snippetmodifier.h"
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/juniper/juniper_separators.h>
#include <vespa/vespalib/stllike/hash_map.hpp>

#include <vespa/log/log.h>
LOG_SETUP(".vsm.snippetmodifier");

using namespace document;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using FieldQueryTermMap = vespalib::hash_map<vsm::FieldIdT, QueryTermList>;

namespace {

void
addIfNotPresent(FieldQueryTermMap & map, vsm::FieldIdT fId, QueryTerm * qt)
{
    auto itr = map.find(fId);
    if (itr != map.end()) {
        QueryTermList & qtl = itr->second;
        if (std::find(qtl.begin(), qtl.end(), qt) == qtl.end()) {
            qtl.push_back(qt);
        }
    } else {
        map[fId].push_back(qt);
    }
}

}

namespace vsm {

void
SnippetModifier::considerSeparator()
{
    if (_useSep) {
        _valueBuf->put(_recordSep);
    }
}

void
SnippetModifier::onPrimitive(uint32_t, const Content & c)
{
    considerSeparator();
    _searcher->onValue(c.getValue());
    _valueBuf->put(_searcher->getModifiedBuf().getBuffer(), _searcher->getModifiedBuf().getPos());
    _useSep = true;
}

void
SnippetModifier::reset()
{
    _valueBuf->reset();
    _useSep = false;
}


SnippetModifier::SnippetModifier(const UTF8SubstringSnippetModifier::SP & searcher) :
    _searcher(searcher),
    _valueBuf(new CharBuffer(32)),
    _recordSep(juniper::separators::record_separator),
    _useSep(false),
    _empty()
{
}

SnippetModifier::SnippetModifier(const UTF8SubstringSnippetModifier::SP & searcher, const CharBuffer::SP & valueBuf) :
    _searcher(searcher),
    _valueBuf(valueBuf),
    _recordSep(juniper::separators::record_separator),
    _useSep(false),
    _empty()
{
}

SnippetModifier::~SnippetModifier() {}

FieldValue::UP
SnippetModifier::modify(const FieldValue & fv, const document::FieldPath & path)
{
    reset();
    fv.iterateNested(path, *this);
    return FieldValue::UP(new StringFieldValue(vespalib::string(_valueBuf->getBuffer(), _valueBuf->getPos())));
}


SnippetModifierManager::SnippetModifierManager() :
    _modifiers(),
    _searchBuf(new SearcherBuf(64)),
    _searchModifyBuf(new CharBuffer(64)),
    _searchOffsetBuf(new std::vector<size_t>(64)),
    _modifierBuf(new CharBuffer(128))
{
}

SnippetModifierManager::~SnippetModifierManager() {}

void
SnippetModifierManager::setup(const QueryTermList& queryTerms,
                              const FieldSearchSpecMapT& specMap,
                              const IndexFieldMapT& indexMap,
                              const vsm::FieldPathMapT& field_paths,
                              search::fef::IQueryEnvironment& query_env)
{
    FieldQueryTermMap fqtm;

    // setup modifiers
    for (auto qt : queryTerms) {
        auto itr = indexMap.find(qt->index());
        if (itr != indexMap.end()) {
            for (auto fId : itr->second) {
                const FieldSearchSpec & spec = specMap.find(fId)->second;
                if (spec.searcher().substring() || qt->isSubstring()) { // we need a modifier for this field id
                    addIfNotPresent(fqtm, fId, qt);
                    if (_modifiers.getModifier(fId) == nullptr) {
                        LOG(debug, "Create snippet modifier for field id '%u'", fId);
                        UTF8SubstringSnippetModifier::SP searcher
                            (new UTF8SubstringSnippetModifier(fId, _searchModifyBuf, _searchOffsetBuf));
                        _modifiers.map()[fId] = std::make_unique<SnippetModifier>(searcher, _modifierBuf);
                    }
                }
            }
        }
    }

    // prepare modifiers
    for (auto & entry : _modifiers.map()) {
        FieldIdT fId = entry.first;
        SnippetModifier & smod = static_cast<SnippetModifier &>(*entry.second);
        smod.getSearcher()->prepare(fqtm[fId], _searchBuf, field_paths, query_env);
    }
}

}
