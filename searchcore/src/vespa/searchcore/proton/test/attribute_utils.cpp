// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_utils.h"
#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/integerbase.h>

using search::attribute::Config;
using search::attribute::BasicType;
using search::attribute::CollectionType;

namespace proton::test {

void
AttributeUtils::fillAttribute(search::AttributeVector & attr, uint32_t numDocs, int64_t value, uint64_t lastSyncToken) {
    search::IntegerAttribute &ia = static_cast<search::IntegerAttribute &>(attr);
    ia.addDocs(numDocs);
    for (uint32_t i = 1; i < ia.getNumDocs(); ++i) {
        ia.update(i, value);
    }
    ia.commit(search::CommitParam(lastSyncToken));
}

void
AttributeUtils::fillAttribute(search::AttributeVector & attr, uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken) {
    search::IntegerAttribute &ia = static_cast<search::IntegerAttribute &>(attr);
    while (ia.getNumDocs() < to) {
        uint32_t docId;
        if (!ia.addDoc(docId)) { HDR_ABORT("should not be reached"); }
    }
    for (uint32_t i = from; i < to; ++i) {
        ia.update(i, value);
    }
    ia.commit(search::CommitParam(lastSyncToken));
}

const Config &
AttributeUtils::getInt32Config() {
    static Config cfg(BasicType::INT32);
    return cfg;
}

const Config &
AttributeUtils::getInt32ArrayConfig() {
    static Config cfg(BasicType::INT32, CollectionType::ARRAY);
    return cfg;
}

const Config &
AttributeUtils::getStringConfig() {
    static Config cfg(BasicType::STRING);
    return cfg;
}

const Config &
AttributeUtils::getPredicateConfig() {
    static Config cfg(BasicType::PREDICATE);
    return cfg;
}

namespace {

Config tensorConfig() {
    return Config(BasicType::TENSOR).setTensorType(vespalib::eval::ValueType::from_spec("tensor(x{},y{})"));
}

}

const Config &
AttributeUtils::getTensorConfig() {
    static Config cfg = tensorConfig();
    return cfg;
}

}

