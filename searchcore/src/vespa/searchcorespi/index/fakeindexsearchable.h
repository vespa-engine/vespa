// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexsearchable.h"
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/blueprint.h>

namespace searchcorespi {

/**
 * A fake index searchable used for unit testing.
 */
class FakeIndexSearchable : public IndexSearchable {
private:
    search::queryeval::FakeSearchable _fake;

public:
    FakeIndexSearchable() : _fake() { }

    search::queryeval::FakeSearchable &getFake() { return _fake; }

    std::unique_ptr<search::queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const Node &term) override
    {
        return _fake.createBlueprint(requestContext, field, term);
    }

    search::SearchableStats getSearchableStats() const override {
        return search::SearchableStats();
    }

    search::SerialNum getSerialNum() const override { return 0; }
    void accept(IndexSearchableVisitor &) const override { }

    search::index::FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        (void) field_name;
        return search::index::FieldLengthInfo();
    }

};

}  // namespace searchcorespi

