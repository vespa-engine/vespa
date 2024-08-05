// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-bucketspaces.h>
#include <vespa/storage/common/bucket_resolver.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <memory>

namespace storage {

/**
 * Immutable implementation of BucketResolver which maintains an explicit
 * mapping from document type to bucket space.
 *
 * If an unknown document type or bucket space is given as an argument,
 * a document::UnknownBucketSpaceException is thrown.
 */
class ConfigurableBucketResolver : public BucketResolver {
public:
    using BucketSpaceMapping = vespalib::hash_map<vespalib::string, document::BucketSpace>;
    const BucketSpaceMapping _type_to_space;
public:
    explicit ConfigurableBucketResolver(BucketSpaceMapping type_to_space) noexcept;
    ~ConfigurableBucketResolver() override;

    document::Bucket bucketFromId(const document::DocumentId&) const override;
    document::BucketSpace bucketSpaceFromName(const vespalib::string& name) const override;
    vespalib::string nameFromBucketSpace(const document::BucketSpace& space) const override;

    static std::shared_ptr<ConfigurableBucketResolver> from_config(
            const vespa::config::content::core::BucketspacesConfig& config);
};

}
