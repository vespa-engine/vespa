// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace proton
{

class SchemaUtil
{
public:
    /**
     * Make new history schema based on new and old schema and old history.
     */
    static search::index::Schema::SP
    makeHistorySchema(const search::index::Schema &newSchema,
                      const search::index::Schema &oldSchema,
                      const search::index::Schema &oldHistory);

    static search::index::Schema::SP
    makeHistorySchema(const search::index::Schema &newSchema,
                      const search::index::Schema &oldSchema,
                      const search::index::Schema &oldHistory,
                      int64_t timestamp);

    /**
     * Iterate through the given schema and fill out the given string vectors.
     */
    static void
    listSchema(const search::index::Schema &schema,
               std::vector<vespalib::string> &fieldNames,
               std::vector<vespalib::string> &fieldDataTypes,
               std::vector<vespalib::string> &fieldCollectionTypes,
               std::vector<vespalib::string> &fieldLocations);
};

} // namespace proton

