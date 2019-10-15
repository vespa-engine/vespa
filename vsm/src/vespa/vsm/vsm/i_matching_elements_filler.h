// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search {
class MatchingElements;
class StructFieldMapper;
}

namespace vsm {

/*
 * Interface class for filling matching elements structure for
 * streaming search.
 */
class IMatchingElementsFiller {
public:
    virtual std::unique_ptr<search::MatchingElements> fill_matching_elements(const search::StructFieldMapper& struct_field_mapper) = 0;
    virtual ~IMatchingElementsFiller() = default;
};
    
}
