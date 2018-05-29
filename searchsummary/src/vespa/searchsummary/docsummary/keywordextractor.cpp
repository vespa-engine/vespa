// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsumstate.h"
#include "keywordextractor.h"
#include "idocsumenvironment.h"
#include <vespa/searchlib/parsequery/stackdumpiterator.h>

/** Tell us what parts of the query we are interested in */

namespace search::docsummary {


bool useful(search::ParseItem::ItemCreator creator)
{
    switch (creator) {
    case search::ParseItem::CREA_ORIG:
        return true;
    default:
        return false;
    }
}


KeywordExtractor::KeywordExtractor(IDocsumEnvironment * env)
    : _env(env),
      _legalPrefixes(NULL),
      _legalIndexes()
{
}


KeywordExtractor::~KeywordExtractor()
{
    while (_legalPrefixes != NULL) {
        IndexPrefix *tmp = _legalPrefixes;
        _legalPrefixes = tmp->_next;
        delete tmp;
    }
}

KeywordExtractor::IndexPrefix::IndexPrefix(const char *prefix, IndexPrefix **list)
    : _prefix(NULL),
      _prefixLen(0),
      _next(NULL)
{
    _prefix = strdup(prefix);
    assert(_prefix != NULL);
    _prefixLen = strlen(prefix);
    _next = *list;
    *list = this;
}

KeywordExtractor::IndexPrefix::~IndexPrefix()
{
    free(_prefix);
}

bool
KeywordExtractor::IndexPrefix::Match(const char *idxName) const
{
    return (strncmp(idxName, _prefix, _prefixLen) == 0);
}

void
KeywordExtractor::AddLegalIndexSpec(const char *spec)
{
    if (spec == NULL)
        return;

    vespalib::string toks(spec); // tokens
    vespalib::string tok; // single token
    size_t           offset; // offset into tokens buffer
    size_t           seppos; // separator position

    offset = 0;
    while ((seppos = toks.find(';', offset)) != vespalib::string::npos) {
        if (seppos == offset) {
            offset++; // don't want empty tokens
        } else {
            tok = toks.substr(offset, seppos - offset);
            offset = seppos + 1;
            if (tok[tok.size() - 1] == '*') {
                tok.resize(tok.size() - 1);
                AddLegalIndexPrefix(tok.c_str());
            } else {
                AddLegalIndexName(tok.c_str());
            }
        }
    }
    if (toks.size() > offset) { // catch last token
        tok = toks.substr(offset);
        if (tok[tok.size() - 1] == '*') {
            tok.resize(tok.size() - 1);
            AddLegalIndexPrefix(tok.c_str());
        } else {
            AddLegalIndexName(tok.c_str());
        }
    }
}


vespalib::string
KeywordExtractor::GetLegalIndexSpec()
{
    vespalib::string spec;

    if (_legalPrefixes != NULL) {
        for (IndexPrefix *pt = _legalPrefixes;
             pt != NULL; pt = pt->_next) {
            if (spec.size() > 0)
                spec.append(';');
            spec.append(pt->_prefix);
            spec.append('*');
        }
    }

    for (Set::const_iterator it(_legalIndexes.begin()), mt(_legalIndexes.end()); it != mt; it++) {
        if (spec.size() > 0)
            spec.append(';');
        spec.append(*it);
    }
    return spec;
}


bool
KeywordExtractor::IsLegalIndex(vespalib::stringref idxS) const
{
    vespalib::string resolvedIdxName;

    if (_env != NULL) {
        resolvedIdxName = _env->lookupIndex(idxS);
    } else {

        if ( ! idxS.empty() ) {
            resolvedIdxName = idxS;
        } else {
            resolvedIdxName = "__defaultindex";
        }
    }

    if (resolvedIdxName.empty())
        return false;

    return (IsLegalIndexPrefix(resolvedIdxName.c_str()) ||
            IsLegalIndexName(resolvedIdxName.c_str()));
}


char *
KeywordExtractor::ExtractKeywords(vespalib::stringref buf) const
{
    search::SimpleQueryStackDumpIterator si(buf);
    char keywordstore[4096]; // Initial storage for keywords buffer
    search::RawBuf keywords(keywordstore, sizeof(keywordstore));

    while (si.next()) {
        search::ParseItem::ItemCreator creator = si.getCreator();
        switch (si.getType()) {
        case search::ParseItem::ITEM_NOT:
            /**
             * @todo Must consider only the first argument on the stack.
             * Difficult without recursion.
             */
            break;

        case search::ParseItem::ITEM_PHRASE:
            {
            // Must take the next arity TERMS and put together
            bool phraseterms_was_added = false;
            int phraseterms = si.getArity();
            for (int i = 0; i < phraseterms; i++) {
                si.next();
                search::ParseItem::ItemType newtype = si.getType();
                if (newtype != search::ParseItem::ITEM_TERM &&
                    newtype != search::ParseItem::ITEM_NUMTERM)
                {
                    // stack syntax error
                    // LOG(debug, "Extracting keywords found a non-term in a phrase");
                    // making a clean escape.
                    keywords.reset();
                    goto iteratorloopend;
                } else {
                    if (!IsLegalIndex(si.getIndexName()))
                        continue;
                    // Found a term
                    vespalib::stringref term = si.getTerm();
                    search::ParseItem::ItemCreator term_creator = si.getCreator();
                    if ( !term.empty() && useful(term_creator)) {
                        // Actual term to add
                        if (phraseterms_was_added) {
                            // Not the first term in the phrase
                            keywords += " ";
                        } else {
                            phraseterms_was_added = true;
                        }

                        keywords.append(term.c_str(), term.size());
                    }
                }
            }
            if (phraseterms_was_added) {
                // Terms was added, so 0-terminate the string
                keywords.append("\0", 1);
            }

            break;
        }
        case search::ParseItem::ITEM_PREFIXTERM:
        case search::ParseItem::ITEM_SUBSTRINGTERM:
        case search::ParseItem::ITEM_EXACTSTRINGTERM:
        case search::ParseItem::ITEM_NUMTERM:
        case search::ParseItem::ITEM_TERM:
            if (!IsLegalIndex(si.getIndexName()))
                continue;
            {
                // add a new keyword
                vespalib::stringref term = si.getTerm();
                if ( !term.empty() && useful(creator)) {
                    // An actual string to add
                    keywords.append(term.c_str(), term.size());
                    keywords.append("\0", 1);
                }
            }
            break;

        default:
            // Do nothing to AND, RANK, OR
            break;
        }
    }
 iteratorloopend:
    // Add a 'blank' keyword
    keywords.append("\0", 1);

    // Must now allocate a string and copy the data from the rawbuf
    void *result = malloc(keywords.GetUsedLen());
    if (result != NULL) {
        memcpy(result, keywords.GetDrainPos(), keywords.GetUsedLen());
    }
    return static_cast<char *>(result);
}

}
