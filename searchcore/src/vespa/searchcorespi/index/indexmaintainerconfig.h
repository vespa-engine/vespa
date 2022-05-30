// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "warmupconfig.h"
#include <vespa/searchlib/common/tunefileinfo.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/vespalib/stllike/string.h>

namespace searchcorespi::index {

/**
 * Class that keeps the config used when constructing an index maintainer.
 */
class IndexMaintainerConfig {
private:
    const vespalib::string _baseDir;
    const WarmupConfig _warmup;
    const size_t _maxFlushed;
    const search::index::Schema _schema;
    const search::SerialNum _serialNum;
    const search::TuneFileAttributes _tuneFileAttributes;

public:
    IndexMaintainerConfig(const vespalib::string &baseDir,
                          const WarmupConfig & warmup,
                          size_t maxFlushed,
                          const search::index::Schema &schema,
                          const search::SerialNum serialNum,
                          const search::TuneFileAttributes &tuneFileAttributes);

    ~IndexMaintainerConfig();

    /**
     * Returns the base directory in which the maintainer will store its indexes.
     */
    const vespalib::string &getBaseDir() const {
        return _baseDir;
    }

    WarmupConfig getWarmup() const { return _warmup; }

    /**
     * Returns the initial schema containing all current index fields.
     */
    const search::index::Schema &getSchema() const {
        return _schema;
    }

    search::SerialNum getSerialNum() const { return _serialNum; }

    /**
     * Returns the specification on how to read/write attribute vector data files.
     */
    const search::TuneFileAttributes &getTuneFileAttributes() const {
        return _tuneFileAttributes;
    }

    size_t getMaxFlushed() const {
        return _maxFlushed;
    }
};

}
