// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>

namespace search::features {

/**
 * This class represents a query term with the relevant data. Now also
 * with an optional attachment of a TermFieldData pointer.
 */
class QueryTerm {
private:
    const fef::ITermData *_termData;
    fef::TermFieldHandle  _handle;
    feature_t             _significance;
    feature_t             _connectedness;
public:
    QueryTerm()
        : _termData(nullptr),
          _handle(fef::IllegalHandle),
          _significance(0),
          _connectedness(0)
    { }
    QueryTerm(const fef::ITermData *td, feature_t sig = 0, feature_t con = 0)
        : _termData(td),
          _handle(fef::IllegalHandle),
          _significance(sig),
          _connectedness(con)
    { }
    const fef::ITermData *termData() const { return _termData; }
    feature_t significance() const { return _significance; }
    feature_t connectedness() const { return _connectedness; }
    fef::TermFieldHandle fieldHandle() const { return _handle; }
    void fieldHandle(fef::TermFieldHandle handle) { _handle = handle; }
    void fieldHandle(const fef::ITermFieldData *fd) {
        if (fd) {
            _handle = fd->getHandle();
        }
    }
};

/**
 * Convenience typedef for a vector of QueryTerm objects.
 */
using QueryTermVector = std::vector<QueryTerm>;

/**
 * This class is a factory for creating QueryTerm objects.
 */
class QueryTermFactory {
public:
    /**
     * Creates a new QueryTerm object for the term with the given term index.
     *
     * @param env the environment used to lookup TermData object, significance, and connectedness.
     * @param termIndex the index to use when looking up the TermData object.
     * @param lookupSignificance whether we should look up the significance for this term.
     * @param lookupConnectedness whether we should look up the connectedness this term has with the previous term.
     */
    static QueryTerm create(const fef::IQueryEnvironment & env, uint32_t termIndex,
                            bool lookupConnectedness = false);
};

class QueryTermHelper {
public:
    QueryTermHelper(const fef::IQueryEnvironment & env);
    const QueryTermVector & terms() const { return *_queryTerms; }
    static const QueryTermVector & lookupAndStoreQueryTerms(const fef::IQueryEnvironment & env, fef::IObjectStore & objectStore);
private:
    static const QueryTermVector * lookupQueryTerms(const fef::IQueryEnvironment & env);
    static QueryTermVector createQueryTermvector(const fef::IQueryEnvironment & env);
    QueryTermVector         _fallBack;
    const QueryTermVector * _queryTerms;
};

}
