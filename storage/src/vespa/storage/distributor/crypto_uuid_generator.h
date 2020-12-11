// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "uuid_generator.h"

namespace storage::distributor {

/**
 * Generates a 128-bit unique identifier (represented as a hex string) from
 * a cryptographically strong source of pseudo-randomness.
 */
class CryptoUuidGenerator : public UuidGenerator {
public:
    ~CryptoUuidGenerator() override = default;
    vespalib::string generate_uuid() const override;
};

}
