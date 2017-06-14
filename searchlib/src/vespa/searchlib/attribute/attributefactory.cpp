// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attributefactory");

namespace search {

using attribute::CollectionType;

AttributeVector::SP
AttributeFactory::createAttribute(const vespalib::string & baseFileName, const Config & cfg)
{
    AttributeVector::SP ret;
    if (cfg.collectionType().type() == CollectionType::ARRAY) {
        if (cfg.fastSearch()) {
            ret = createArrayFastSearch(baseFileName, cfg);
            if (ret.get() == NULL) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type array<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    baseFileName.c_str(), cfg.basicType().asString());
                ret = createArrayStd(baseFileName, cfg);
            }
        } else {
            ret = createArrayStd(baseFileName, cfg);
        }
    } else if (cfg.collectionType().type() == CollectionType::WSET) {
        // Ignore if noupdate has been set.
        if (cfg.fastSearch()) {
            ret = createSetFastSearch(baseFileName, cfg);
            if (ret.get() == NULL) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type set<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    baseFileName.c_str(), cfg.basicType().asString());
                ret = createSetStd(baseFileName, cfg);
            }
        } else {
            ret = createSetStd(baseFileName, cfg);
        }
    } else {
        if (cfg.fastSearch()) {
            ret = createSingleFastSearch(baseFileName, cfg);
            if (ret.get() == NULL) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type %s. "
                    "Falling back to normal. You should correct your .sd file.",
                    baseFileName.c_str(), cfg.basicType().asString());
                ret = createSingleStd(baseFileName, cfg);
            }
        } else {
            ret = createSingleStd(baseFileName, cfg);
        }
    }
    return ret;
}

}
