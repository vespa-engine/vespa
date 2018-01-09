// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/persistence/spi/fixed_bucket_spaces.h>
#include <vespa/vespalib/util/exceptions.h>
#include "configurable_bucket_resolver.h"

namespace storage {

document::Bucket ConfigurableBucketResolver::bucketFromId(const document::DocumentId& id) const {
    auto iter = _type_to_space.find(id.getDocType());
    if (iter != _type_to_space.end()) {
        return document::Bucket(iter->second, document::BucketId(0));
    }
    throw spi::UnknownBucketSpaceException("Unknown bucket space mapping for document type '"
                                           + id.getDocType() + "' in id: '" + id.toString() + "'", VESPA_STRLOC);
}

document::BucketSpace ConfigurableBucketResolver::bucketSpaceFromName(const vespalib::string& name) const {
    return spi::FixedBucketSpaces::from_string(name);
}

vespalib::string ConfigurableBucketResolver::nameFromBucketSpace(const document::BucketSpace& space) const {
    return spi::FixedBucketSpaces::to_string(space);
}

std::shared_ptr<ConfigurableBucketResolver> ConfigurableBucketResolver::from_config(
        const vespa::config::content::core::BucketspacesConfig& config) {
    ConfigurableBucketResolver::BucketSpaceMapping type_to_space;
    for (auto& mapping : config.documenttype) {
        type_to_space.emplace(mapping.name, spi::FixedBucketSpaces::from_string(mapping.bucketspace));
    }
    return std::make_shared<ConfigurableBucketResolver>(std::move(type_to_space));
}

}
