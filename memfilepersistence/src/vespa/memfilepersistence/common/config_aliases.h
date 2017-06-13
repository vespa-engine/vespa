// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config-stor-memfilepersistence.h>
#include <vespa/config-stor-devices.h>
#include <vespa/config-persistence.h>

namespace storage {
namespace memfile {

// Friendly aliases to painfully long config names.
using MemFilePersistenceConfig= vespa::config::storage::StorMemfilepersistenceConfig;
using PersistenceConfig = vespa::config::content::PersistenceConfig;
using DevicesConfig = vespa::config::storage::StorDevicesConfig;

} // memfile
} // storage

