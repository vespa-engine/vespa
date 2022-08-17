// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/common/schema.h>

namespace search::index {

/**
 * Fields from an index schema used to represent an uri.
 **/
class UriField
{
public:
    using UsedFieldsMap = std::vector<bool>;

    uint32_t _all;
    uint32_t _scheme;
    uint32_t _host;
    uint32_t _port;
    uint32_t _path;
    uint32_t _query;
    uint32_t _fragment;
    uint32_t _hostname;

private:
    static void markUsed(UsedFieldsMap &usedFields, uint32_t field);
    static bool valid(const Schema &schema, uint32_t fieldId,
                      const Schema::CollectionType &collectionType);

public:
    UriField();

    bool broken(const Schema &schema, const Schema::CollectionType &collectionType) const;
    bool valid(const Schema &schema, const Schema::CollectionType &collectionType) const;
    void setup(const Schema &schema, const vespalib::string &field);
    void markUsed(UsedFieldsMap &usedFields) const;
    static bool mightBePartofUri(vespalib::stringref name);
};

}
