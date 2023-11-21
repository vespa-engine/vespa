// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/stllike/string.h>
#include <utility>

namespace search::query {

/*
 * Interface class for terms owned by a MultiTerm query node.
 */
class TermVector {
public:
    using StringAndWeight = std::pair<vespalib::stringref, Weight>;
    using IntegerAndWeight = std::pair<int64_t, Weight>;
    virtual ~TermVector() = default;
    virtual void addTerm(vespalib::stringref term, Weight weight) = 0;
    virtual void addTerm(int64_t term, Weight weight) = 0;
    [[nodiscard]] virtual StringAndWeight getAsString(uint32_t index) const = 0;
    [[nodiscard]] virtual IntegerAndWeight getAsInteger(uint32_t index) const = 0;
    [[nodiscard]] virtual Weight getWeight(uint32_t index) const = 0;
    [[nodiscard]] virtual uint32_t size() const = 0;
};

}
