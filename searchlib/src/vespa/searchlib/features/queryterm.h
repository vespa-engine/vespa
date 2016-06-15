// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/fef/iqueryenvironment.h>
#include <vespa/searchlib/fef/itermdata.h>

namespace search {
namespace features {

/**
 * This class represents a query term with the relevant data. Now also
 * with an optional attachment of a TermFieldData pointer.
 */
class QueryTerm {
private:
    const fef::ITermData *_termData;
    fef::TermFieldHandle _handle;
    feature_t _significance;
    feature_t _connectedness;
public:
    QueryTerm();
    QueryTerm(const fef::ITermData *td, feature_t sig = 0, feature_t con = 0);
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
typedef std::vector<QueryTerm> QueryTermVector;

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
    static QueryTerm create(const fef::IQueryEnvironment & env,
                            uint32_t termIndex,
                            bool lookupSignificance = true,
                            bool lookupConnectedness = false);
};


} // namespace features
} // namespace search

