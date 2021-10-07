// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "domainconfig.h"

namespace search::transactionlog {

DomainConfig::DomainConfig()
    : _encoding(Encoding::Crc::xxh64, Encoding::Compression::none),
      _compressionLevel(9),
      _fSyncOnCommit(false),
      _partSizeLimit(0x10000000), // 256M
      _chunkSizeLimit(0x40000)   // 256k
{ }

}
