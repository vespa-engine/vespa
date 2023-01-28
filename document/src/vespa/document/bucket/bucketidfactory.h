// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class document::BucketIdFactory
 * \ingroup bucket
 *
 * \brief Class to generate bucket identifiers from document identifiers.
 *
 * To be able to take advantage of some pregenerated state to make bucket id
 * creation efficient, this class exist to prevent the need for static objects
 * in the bucket identifier.
 *
 * \see BucketId for more information.
 */

#pragma once

#include <vespa/document/util/printable.h>
#include <cstdint>

namespace document {

class BucketId;
class DocumentId;

class BucketIdFactory : public document::Printable
{
    uint16_t _locationBits;
    uint16_t _gidBits;
    uint16_t _countBits;
    uint64_t _locationMask;
    uint64_t _gidMask;
    uint64_t _initialCount;

public:
    BucketIdFactory();

    BucketId getBucketId(const DocumentId&) const;
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

private:
    void initializeMasks();
};

} // document
