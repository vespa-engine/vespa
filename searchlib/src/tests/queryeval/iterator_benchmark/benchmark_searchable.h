// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace search::query { class Node; }

namespace search::queryeval {
class Blueprint;
class FieldSpec;
class IRequestContext;
}

namespace search::queryeval::test {

/**
 * Simplified interface used to create a Blueprint, similar to search::queryeval::Searchable.
 */
class BenchmarkSearchable {
public:
    virtual ~BenchmarkSearchable() = default;
    virtual std::unique_ptr<Blueprint> create_blueprint(const FieldSpec& field,
                                                        const search::query::Node& term) = 0;
};

}
