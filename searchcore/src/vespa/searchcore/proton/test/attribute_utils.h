// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/integerbase.h>

namespace proton {

namespace test {

struct AttributeUtils
{
    static void fillAttribute(const search::AttributeVector::SP &attr,
                              uint32_t numDocs, int64_t value, uint64_t lastSyncToken) {
        search::IntegerAttribute &ia = static_cast<search::IntegerAttribute &>(*attr);
        ia.addDocs(numDocs);
        for (uint32_t i = 1; i < ia.getNumDocs(); ++i) {
            ia.update(i, value);
        }
        ia.commit(lastSyncToken, lastSyncToken);
    }
    static void fillAttribute(const search::AttributeVector::SP &attr,
                              uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken) {
        search::IntegerAttribute &ia = static_cast<search::IntegerAttribute &>(*attr);
        while (ia.getNumDocs() < to) {
            uint32_t docId;
            if (!ia.addDoc(docId)) { HDR_ABORT("should not be reached"); }
        }
        for (uint32_t i = from; i < to; ++i) {
            ia.update(i, value);
        }
        ia.commit(lastSyncToken, lastSyncToken);
    }
    static search::attribute::Config getInt32Config() {
        return search::attribute::Config(search::attribute::BasicType::INT32);
    }
    static search::attribute::Config getInt32ArrayConfig() {
        return search::attribute::Config(search::attribute::BasicType::INT32,
         search::attribute::CollectionType::ARRAY);
    }
    static search::attribute::Config getStringConfig() {
        return search::attribute::Config(search::attribute::BasicType::STRING);
    }
    static search::attribute::Config getPredicateConfig() {
        return search::attribute::Config(search::attribute::BasicType::PREDICATE);
    }
    static search::attribute::Config getTensorConfig() {
        return search::attribute::Config(search::attribute::BasicType::TENSOR);
    }
};

} // namespace test

} // namespace proton

