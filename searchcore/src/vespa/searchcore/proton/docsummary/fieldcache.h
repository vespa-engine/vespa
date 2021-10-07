// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/searchsummary/docsummary/resultclass.h>

namespace proton {

/**
 * A cache of document::Field instances that is associated
 * with a summary result class.
 **/
class FieldCache
{
private:
    typedef std::vector<document::Field::CSP> Cache;

    Cache _cache;

public:
    typedef std::shared_ptr<const FieldCache> CSP;

    FieldCache();

    FieldCache(const search::docsummary::ResultClass &resClass,
               const document::DocumentType &docType);

    size_t size() const { return _cache.size(); }

    const document::Field *getField(size_t idx) const {
        return _cache[idx].get();
    }
};

} // namespace proton

