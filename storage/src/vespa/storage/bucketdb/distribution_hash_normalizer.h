// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage {

/**
 * Utility class for "normalizing" a received distribution hash string into
 * a representation that is ordering invariant across group and node indices.
 *
 * All group indices and node indices will be returned in increasing order.
 *
 * In the case of a parser error the original string will be returned verbatim.
 */
class DistributionHashNormalizer {
    // PIMPL the parser to avoid Spirit deps in header file.
    struct ParserImpl;
    std::unique_ptr<ParserImpl> _impl;
public:
    DistributionHashNormalizer();
    ~DistributionHashNormalizer();

    vespalib::string normalize(vespalib::stringref hash) const;
};

} // storage

