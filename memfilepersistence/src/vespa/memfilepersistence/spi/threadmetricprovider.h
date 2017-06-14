// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace storage {
namespace memfile {

class MemFilePersistenceThreadMetrics;

class ThreadMetricProvider
{
public:
    virtual ~ThreadMetricProvider() {}

    virtual MemFilePersistenceThreadMetrics& getMetrics() const = 0;
};

}
}
