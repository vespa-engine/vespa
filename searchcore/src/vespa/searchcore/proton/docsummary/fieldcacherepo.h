// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include "fieldcache.h"

namespace proton {

/**
 * A repository of FieldCache instances,
 * one for each summary result class that we have in the summary result config.
 **/
class FieldCacheRepo
{
private:
    typedef std::map<vespalib::string, FieldCache::CSP> Repo;

    Repo _repo;
    FieldCache::CSP _defaultCache; // corresponds to the default summary class

public:
    typedef std::unique_ptr<FieldCacheRepo> UP;

    FieldCacheRepo();

    FieldCacheRepo(const search::docsummary::ResultConfig &resConfig,
                   const document::DocumentType &docType);

    FieldCache::CSP getFieldCache(const vespalib::string &resultClass) const;

};

} // namespace proton

