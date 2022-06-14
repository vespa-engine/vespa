// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "attributevector.h"
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attributefactory");

namespace search {

using attribute::CollectionType;

AttributeVector::SP
AttributeFactory::createAttribute(stringref name, const Config & cfg)
{
    AttributeVector::SP ret;
    if (cfg.collectionType().type() == CollectionType::ARRAY) {
        if (cfg.fastSearch()) {
            ret = createArrayFastSearch(name, cfg);
            if ( ! ret) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type array<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.data(), cfg.basicType().asString());
                ret = createArrayStd(name, cfg);
            }
        } else {
            ret = createArrayStd(name, cfg);
        }
    } else if (cfg.collectionType().type() == CollectionType::WSET) {
        // Ignore if noupdate has been set.
        if (cfg.fastSearch()) {
            ret = createSetFastSearch(name, cfg);
            if ( ! ret) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type set<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.data(), cfg.basicType().asString());
                ret = createSetStd(name, cfg);
            }
        } else {
            ret = createSetStd(name, cfg);
        }
    } else {
        if (cfg.fastSearch()) {
            ret = createSingleFastSearch(name, cfg);
            if ( ! ret) {
                LOG(warning, "Cannot apply fastsearch hint on attribute %s of type %s. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.data(), cfg.basicType().asString());
                ret = createSingleStd(name, cfg);
            }
        } else {
            ret = createSingleStd(name, cfg);
        }
    }
    return ret;
}

}
