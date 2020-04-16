// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::Matcher
 * \ingroup spi
 *
 * \brief Use a matcher to find what documents one is interested in.
 */

#pragma once

#include "docentry.h"
#include <persistence/spi/documentsubset.h>
#include <persistence/spi/types.h>

namespace storage::spi {

class Matcher {
    DocumentSubset _subset;

public:
    Matcher(const DocumentSubset& subset) : _subset(subset) {}
    virtual ~Matcher() {}

    virtual bool match(const DocEntry&) const = 0;

    /**
     * Get the document subset that this matcher needs in order to decide
     * whether a document entry should be matched or not. When match is called,
     * specified information is guarantueed to be set.
     */
    const DocumentSubset& getNeededParts() const { return _subset; }
};

struct AllMatcher : public Matcher {
    AllMatcher() : Matcher(DocumentSubset(0)) {}
    bool match(const DocEntry&) const { return true; }
};

}
