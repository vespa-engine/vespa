
// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "tunefileinfo.h"
#include <sys/mman.h>
#include <fcntl.h>

namespace search {

template <typename TuneControlConfig, typename MMapConfig>
void
TuneFileRandRead::setFromConfig(const enum TuneControlConfig::Io & tuneControlConfig, const MMapConfig & mmapFlags) {
    switch ( tuneControlConfig) {
        case TuneControlConfig::NORMAL:   _tuneControl = NORMAL; break;
        case TuneControlConfig::DIRECTIO: _tuneControl = DIRECTIO; break;
        case TuneControlConfig::MMAP:     _tuneControl = MMAP; break;
        default:                          _tuneControl = NORMAL; break;
    }
    setFromMmapConfig(mmapFlags);
}

template <typename MMapConfig>
void
TuneFileRandRead::setFromMmapConfig(const MMapConfig & mmapFlags) {
    for (size_t i(0), m(mmapFlags.options.size()); i < m; i++) {
        switch (mmapFlags.options[i]) {
            case MMapConfig::MLOCK:    _mmapFlags |= MAP_LOCKED; break;
            case MMapConfig::POPULATE: _mmapFlags |= MAP_POPULATE; break;
            case MMapConfig::HUGETLB:  _mmapFlags |= MAP_HUGETLB; break;
        }
    }
    switch (mmapFlags.advise) {
        case MMapConfig::NORMAL:     setAdvise(POSIX_FADV_NORMAL); break;
        case MMapConfig::RANDOM:     setAdvise(POSIX_FADV_RANDOM); break;
        case MMapConfig::SEQUENTIAL: setAdvise(POSIX_FADV_SEQUENTIAL); break;
    }
}

}

