// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "domainconfig.h"
#include <vespa/vespalib/util/exceptions.h>

namespace search::transactionlog {

DomainConfig::DomainConfig()
    : _encoding(Encoding::Crc::xxh64, Encoding::Compression::zstd),
      _compressionLevel(9),
      _fSyncOnCommit(false),
      _partSizeLimit(0x10000000), // 256M
      _chunkSizeLimit(0x40000)   // 256k
{ }

DomainConfig &
DomainConfig::setEncoding(Encoding v) {
    if (v.getCompression() == Encoding::none) {
        throw vespalib::IllegalArgumentException("Compression:none is not allowed for the tls", VESPA_STRLOC);
    }
    _encoding = v;
    return *this;
}

}
