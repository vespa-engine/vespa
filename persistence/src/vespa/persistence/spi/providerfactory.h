// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::ProviderFactory
 * \ingroup spi
 *
 * \brief Factory class to generate a persistence provider interface
 */

#pragma once

#include "persistenceprovider.h"

namespace document { class DocumentTypeRepo; }

namespace storage::spi {

struct ProviderFactory {
    virtual ~ProviderFactory() {}

    virtual PersistenceProvider::UP createProviderInstance(
            document::DocumentTypeRepo&) = 0;
};

} // spi
} // storage


