// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include "fake_result.h"
#include <map>
#include <string>

namespace search::queryeval {

/**
 * A fake Searchable implementation.
 **/
class FakeSearchable : public Searchable
{
private:
    using Key = std::pair<std::string, std::string>;
    using Map = std::map<Key, FakeResult>;

    std::string _tag;
    Map              _map;
    bool             _is_attr;

public:
    /**
     * Create an initially empty fake searchable.
     **/
    FakeSearchable();

    /**
     * Tag this searchable with a string value that will be visible
     * when dumping search iterators created from it.
     *
     * @return this object for chaining
     * @param t tag
     **/
    FakeSearchable &tag(const std::string &t) {
        _tag = t;
        return *this;
    }

    /**
     * Is this searchable searching attributes? Setting this to true
     * will result in blueprints and search iterators exposing a
     * mocked attribute search context interface.
     **/
    FakeSearchable &is_attr(bool value) {
        _is_attr = value;
        return *this;
    }

    /**
     * Add a fake result to be returned for lookup on the given field
     * and term combination.
     *
     * @return this object for chaining
     * @param field field name
     * @param term search term in string form
     * @param result the fake result
     **/
    FakeSearchable &addResult(const std::string &field,
                              const std::string &term,
                              const FakeResult &result);

    using Searchable::createBlueprint;
    std::unique_ptr<queryeval::Blueprint>
    createBlueprint(const IRequestContext & requestContext,
                    const FieldSpec &field,
                    const search::query::Node &term) override;
    ~FakeSearchable() override;
};

}
