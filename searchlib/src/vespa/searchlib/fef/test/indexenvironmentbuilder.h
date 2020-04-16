// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexenvironment.h"

namespace search::fef::test {

/**
 * This class is used to setup an IndexEnvironment for testing.
 */
class IndexEnvironmentBuilder {
public:
    /**
     * Constructs a new index environment builder.
     *
     * @param env The index environment to build to.
     */
    IndexEnvironmentBuilder(IndexEnvironment &env);

    /**
     * Add a field to the index environment. This is analogous to adding fields to a document.
     *
     * @param type The type of field to add.
     * @param coll collection type
     * @param name The name of the field.
     */
    IndexEnvironmentBuilder &addField(const FieldType &type,
                                      const FieldInfo::CollectionType &coll,
                                      const vespalib::string &name);

    /**
     * Add a field to the index environment with specified data type.
     *
     * @param type      The type of field to add.
     * @param coll      Collection type
     * @param dataType  Collection base data type
     * @param name      The name of the field.
     */
    IndexEnvironmentBuilder &addField(const FieldType &type,
                                      const FieldInfo::CollectionType &coll,
                                      const FieldInfo::DataType &dataType,
                                      const vespalib::string &name);

    /** Returns a reference to the index environment of this. */
    IndexEnvironment &getIndexEnv() { return _env; }

    /** Returns a const reference to the index environment of this. */
    const IndexEnvironment &getIndexEnv() const { return _env; }

private:
    IndexEnvironmentBuilder(const IndexEnvironmentBuilder &);             // hide
    IndexEnvironmentBuilder & operator=(const IndexEnvironmentBuilder &); // hide

private:
    IndexEnvironment &_env;
};

}
