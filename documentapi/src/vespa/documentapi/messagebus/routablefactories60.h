// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "routablefactories52.h"

namespace documentapi {
/**
 * This class encapsulates all the {@link RoutableFactory} classes needed to implement factories for the document
 * routable. When adding new factories to this class, please KEEP THE THEM ORDERED alphabetically like they are now.
 */
class RoutableFactories60 : public RoutableFactories52 {
public:
    RoutableFactories60() = delete;

    // TODO dedupe bucket space codec code

    class CreateVisitorMessageFactory : public RoutableFactories52::CreateVisitorMessageFactory {
        bool encodeBucketSpace(vespalib::stringref bucketSpace, vespalib::GrowableByteBuffer& buf) const override;
        string decodeBucketSpace(document::ByteBuffer&) const override;
    public:
        CreateVisitorMessageFactory(const document::DocumentTypeRepo& r)
            : RoutableFactories52::CreateVisitorMessageFactory(r) {}
    };

    class StatBucketMessageFactory : public RoutableFactories50::StatBucketMessageFactory {
        bool encodeBucketSpace(vespalib::stringref bucketSpace, vespalib::GrowableByteBuffer& buf) const override;
        string decodeBucketSpace(document::ByteBuffer&) const override;
    public:
        StatBucketMessageFactory() = default;
    };

    class GetBucketListMessageFactory : public RoutableFactories50::GetBucketListMessageFactory {
        bool encodeBucketSpace(vespalib::stringref bucketSpace, vespalib::GrowableByteBuffer& buf) const override;
        string decodeBucketSpace(document::ByteBuffer&) const override;
    public:
        GetBucketListMessageFactory() = default;
    };

    static void doEncodeBucketSpace(vespalib::stringref bucketSpace, vespalib::GrowableByteBuffer& buf);
    static string doDecodeBucketSpace(document::ByteBuffer&);
};

}