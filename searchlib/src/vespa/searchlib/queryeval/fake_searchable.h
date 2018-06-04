// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include "fake_result.h"

#include <string>
#include <map>

namespace search::queryeval {

/**
 * A fake Searchable implementation.
 **/
class FakeSearchable : public Searchable
{
private:
    typedef std::pair<vespalib::string, vespalib::string> Key;
    typedef FakeResult                          Value;
    typedef std::map<Key, Value>                Map;

    vespalib::string _tag;
    Map         _map;

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
    FakeSearchable &tag(const vespalib::string &t) {
        _tag = t;
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
    FakeSearchable &addResult(const vespalib::string &field,
                              const vespalib::string &term,
                              const FakeResult &result);

    using Searchable::createBlueprint;
    Blueprint::UP createBlueprint(const IRequestContext & requestContext,
                                  const FieldSpec &field,
                                  const search::query::Node &term) override;
    ~FakeSearchable();
};

}
