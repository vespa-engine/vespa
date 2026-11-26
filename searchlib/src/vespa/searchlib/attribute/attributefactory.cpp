// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"

#include "attributevector.h"

#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attributefactory");

namespace search {

using attribute::BasicType;
using attribute::CollectionType;
using vespalib::IllegalArgumentException;

AttributeVector::SP AttributeFactory::createAttribute(string_view name_view, const Config& cfg) {
    std::string         name(name_view);
    AttributeVector::SP ret;
    if (cfg.tensor_label_postings() && (cfg.basicType().type() != BasicType::TENSOR ||
                                        cfg.collectionType().type() != CollectionType::SINGLE || !cfg.fastSearch()))
    {
        throw IllegalArgumentException("Tensor label postings for attribute " + name +
                                       " require a single value tensor attribute with fast-search");
    }
    if (cfg.collectionType().type() == CollectionType::ARRAY) {
        if (cfg.fastSearch()) {
            ret = createArrayFastSearch(name, cfg);
            if (!ret) {
                LOG(warning,
                    "Cannot apply fastsearch hint on attribute %s of type array<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.c_str(), cfg.basicType().asString());
                ret = createArrayStd(name, cfg);
            }
        } else {
            ret = createArrayStd(name, cfg);
        }
    } else if (cfg.collectionType().type() == CollectionType::WSET) {
        // Ignore if noupdate has been set.
        if (cfg.fastSearch()) {
            ret = createSetFastSearch(name, cfg);
            if (!ret) {
                LOG(warning,
                    "Cannot apply fastsearch hint on attribute %s of type set<%s>. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.c_str(), cfg.basicType().asString());
                ret = createSetStd(name, cfg);
            }
        } else {
            ret = createSetStd(name, cfg);
        }
    } else {
        if (cfg.fastSearch()) {
            ret = createSingleFastSearch(name, cfg);
            if (!ret) {
                LOG(warning,
                    "Cannot apply fastsearch hint on attribute %s of type %s. "
                    "Falling back to normal. You should correct your .sd file.",
                    name.c_str(), cfg.basicType().asString());
                ret = createSingleStd(name, cfg);
            }
        } else {
            ret = createSingleStd(name, cfg);
        }
    }
    return ret;
}

} // namespace search
