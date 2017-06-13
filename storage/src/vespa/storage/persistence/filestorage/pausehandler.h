// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class PauseHandler
 * @ingroup persistence
 *
 * @brief Object that can be used to possibly pause running operation
 */
#pragma once

#include <vespa/storage/persistence/filestorage/filestorhandler.h>

namespace storage {

class PauseHandler {
    FileStorHandler* _handler;
    uint16_t _disk;
    uint8_t _priority;

public:
    PauseHandler() : _handler(0), _disk(0), _priority(0) {}
    PauseHandler(FileStorHandler& handler, uint16_t disk)
        : _handler(&handler),
          _disk(disk),
          _priority(0)
    {
    }

    void setPriority(uint8_t priority) { _priority = priority; }

    void pause() const { if (_handler != 0) _handler->pause(_disk, _priority); }
};

} // storage

