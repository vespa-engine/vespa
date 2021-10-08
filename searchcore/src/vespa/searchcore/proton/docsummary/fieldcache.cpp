// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fieldcache.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.fieldcache");

using namespace document;
using namespace search::docsummary;

namespace proton {

FieldCache::FieldCache() :
    _cache()
{
}

FieldCache::FieldCache(const ResultClass &resClass,
                       const DocumentType &docType) :
    _cache()
{
    LOG(debug, "Creating field cache for summary class '%s'", resClass.GetClassName());
    for (uint32_t i = 0; i < resClass.GetNumEntries(); ++i) {
        const ResConfigEntry *entry = resClass.GetEntry(i);
        const vespalib::string fieldName(entry->_bindname);
        if (docType.hasField(fieldName)) {
            const Field &field = docType.getField(fieldName);
            LOG(debug, "Caching Field instance for field '%s': %s.%u",
                fieldName.c_str(), field.getName().data(), field.getId());
            _cache.push_back(std::make_shared<const Field>(field));
        } else {
            _cache.push_back(Field::CSP());
        }
    }
}

} // namespace proton
