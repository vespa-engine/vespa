// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tunefileinfo.h"
#include <sys/mman.h>
#include <fcntl.h>

namespace search {

template <typename TuneControlConfig, typename MMapConfig>
void
TuneFileRandRead::setFromConfig(const enum TuneControlConfig::Io & tuneControlConfig, const MMapConfig & mmapFlags) {
    switch ( tuneControlConfig) {
        case TuneControlConfig::Io::NORMAL:   _tuneControl = NORMAL; break;
        case TuneControlConfig::Io::DIRECTIO: _tuneControl = DIRECTIO; break;
        case TuneControlConfig::Io::MMAP:     _tuneControl = MMAP; break;
        default:                          _tuneControl = NORMAL; break;
    }
    setFromMmapConfig(mmapFlags);
}

template <typename MMapConfig>
void
TuneFileRandRead::setFromMmapConfig(const MMapConfig & mmapFlags) {
    for (size_t i(0), m(mmapFlags.options.size()); i < m; i++) {
#ifdef __linux__
        switch (mmapFlags.options[i]) {
            case MMapConfig::Options::POPULATE: _mmapFlags |= MAP_POPULATE; break;
            case MMapConfig::Options::HUGETLB:  _mmapFlags |= MAP_HUGETLB; break;
        }
#endif
    }
#ifdef __linux__
    switch (mmapFlags.advise) {
        case MMapConfig::Advise::NORMAL:     setAdvise(POSIX_FADV_NORMAL); break;
        case MMapConfig::Advise::RANDOM:     setAdvise(POSIX_FADV_RANDOM); break;
        case MMapConfig::Advise::SEQUENTIAL: setAdvise(POSIX_FADV_SEQUENTIAL); break;
    }
#endif
}

}

