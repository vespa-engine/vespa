// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldcacherepo.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.fieldcacherepo");

using namespace document;
using namespace search::docsummary;

namespace proton {

FieldCacheRepo::FieldCacheRepo() :
    _repo(),
    _defaultCache(std::make_shared<const FieldCache>())
{
}

FieldCacheRepo::FieldCacheRepo(const ResultConfig &resConfig,
                               const DocumentType &docType) :
    _repo(),
    _defaultCache(std::make_shared<const FieldCache>())
{
    for (ResultConfig::const_iterator it(resConfig.begin()), mt(resConfig.end()); it != mt; it++) {
        auto cache = std::make_shared<const FieldCache>(*it, docType);
        vespalib::string className(it->GetClassName());
        LOG(debug, "Adding field cache for summary class '%s' to repo",
            className.c_str());
        _repo.insert(std::make_pair(className, cache));
    }
    const ResultClass *defaultClass = resConfig.LookupResultClass(resConfig.LookupResultClassId(""));
    if (defaultClass != NULL) {
        _defaultCache = getFieldCache(defaultClass->GetClassName());
    }
}

FieldCache::CSP
FieldCacheRepo::getFieldCache(const vespalib::string &resultClass) const
{
    Repo::const_iterator itr = _repo.find(resultClass);
    if (itr != _repo.end()) {
        return itr->second;
    }
    return _defaultCache;
}

} // namespace proton
