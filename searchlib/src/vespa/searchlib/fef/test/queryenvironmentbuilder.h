// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/fef/matchdatalayout.h>
#include "queryenvironment.h"

namespace search {
namespace fef {
namespace test {

class QueryEnvironmentBuilder {
public:
    /**
     * Constructs a new query environment builder.
     *
     * @param queryEnv The query environment to build in.
     * @param layout   The layout of match data to simultaneously update.
     */
    QueryEnvironmentBuilder(QueryEnvironment &queryEnv, MatchDataLayout &layout);
    ~QueryEnvironmentBuilder();

    /**
     * Add a term node searching all known fields to this query
     * environment. This will update both the environment and the
     * match data layout.
     *
     * @return Reference to the corresponding term data.
     */
    SimpleTermData &addAllFields();

    /**
     * Add a term node searching in the given fields to this query
     * environment.  This will update both the environment and the
     * match data layout. All fields are required to be of type INDEX.
     *
     * @return Pointer to the corresponding term data or NULL if one of the fields does not exists.
     */
    SimpleTermData *addIndexNode(const std::vector<vespalib::string> &fieldNames);

    /**
     * Add an attribute node searching in the given attribute to this query environment.
     * This will update both the environment and the match data layout.
     *
     * @return Pointer to the corresponding term data or NULL if attribute does not exists.
     */
    SimpleTermData *addAttributeNode(const vespalib::string & attrName);

    /**
     * Add a term node searching in the given virtual field.
     */
    SimpleTermData *add_virtual_node(const vespalib::string &virtual_field);

    /** Returns a reference to the query environment of this. */
    QueryEnvironment &getQueryEnv() { return _queryEnv; }

    /** Returns a const reference to the query environment of this. */
    const QueryEnvironment &getQueryEnv() const { return _queryEnv; }

    /** Returns a reference to the match data layout of this. */
    MatchDataLayout &getLayout() { return _layout; }

    /** Returns a const reference to the match data layout of this. */
    const MatchDataLayout &getLayout() const { return _layout; }

    QueryEnvironmentBuilder& set_avg_field_length(const vespalib::string& field_name, double avg_field_length);

private:
    QueryEnvironmentBuilder(const QueryEnvironmentBuilder &);             // hide
    QueryEnvironmentBuilder & operator=(const QueryEnvironmentBuilder &); // hide
    SimpleTermData *add_node(const FieldInfo &info);

private:
    QueryEnvironment &_queryEnv;
    MatchDataLayout  &_layout;
};

} // namespace test
} // namespace fef
} // namespace search

