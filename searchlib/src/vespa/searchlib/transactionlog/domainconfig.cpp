// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

DomainInfo::DomainInfo(SerialNumRange range_in, size_t numEntries_in, size_t byteSize_in, uint64_t size_on_disk_in, DurationSeconds maxSessionRunTime_in)
    : range(range_in), numEntries(numEntries_in), byteSize(byteSize_in), size_on_disk(size_on_disk_in),
      maxSessionRunTime(maxSessionRunTime_in), parts()
{
}

DomainInfo::DomainInfo()
    : range(), numEntries(0), byteSize(0), size_on_disk(0), maxSessionRunTime(), parts()
{
}

DomainInfo::~DomainInfo() = default;

}
