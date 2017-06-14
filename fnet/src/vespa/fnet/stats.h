// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

/**
 * This class is used internally by @ref FNET_Transport objects to
 * aggregate FNET statistics. The actual statistics are located in the
 * @ref FNET_Stats class.
 **/
class FNET_StatCounters
{
public:
    uint32_t _eventLoopCnt;   // # event loop iterations
    uint32_t _eventCnt;       // # internal events
    uint32_t _ioEventCnt;     // # IO events
    uint32_t _packetReadCnt;  // # packets read
    uint32_t _packetWriteCnt; // # packets written
    uint32_t _dataReadCnt;    // # bytes read
    uint32_t _dataWriteCnt;   // # bytes written

    FNET_StatCounters();
    ~FNET_StatCounters();

    void Clear();
    void CountEventLoop(uint32_t cnt)   { _eventLoopCnt   += cnt;   }
    void CountEvent(uint32_t cnt)       { _eventCnt       += cnt;   }
    void CountIOEvent(uint32_t cnt)     { _ioEventCnt     += cnt;   }
    void CountPacketRead(uint32_t cnt)  { _packetReadCnt  += cnt;   }
    void CountPacketWrite(uint32_t cnt) { _packetWriteCnt += cnt;   }
    void CountDataRead(uint32_t bytes)  { _dataReadCnt    += bytes; }
    void CountDataWrite(uint32_t bytes) { _dataWriteCnt   += bytes; }
};

//-----------------------------------------------

#define FNET_STATS_OLD_FACTOR 0.5
#define FNET_STATS_NEW_FACTOR 0.5

/**
 * This class contains various FNET statistics. The statistics for a
 * @ref FNET_Transport object may be obtained by invoking the GetStats
 * method on it.
 **/
class FNET_Stats
{
public:
    /**
     * Event loop iterations per second.
     **/
    float _eventLoopRate;   // loop iterations/s

    /**
     * Internal events handled per second.
     **/
    float _eventRate;       // internal-events/s

    /**
     * IO events handled per second.
     **/
    float _ioEventRate;     // IO-events/s

    /**
     * Packets read per second.
     **/
    float _packetReadRate;  // packets/s

    /**
     * Packets written per second.
     **/
    float _packetWriteRate; // packets/s

    /**
     * Data read per second (in kB).
     **/
    float _dataReadRate;    // kB/s

    /**
     * Data written per second (in kB).
     **/
    float _dataWriteRate;   // kB/s

    FNET_Stats();
    ~FNET_Stats();

    /**
     * Update statistics. The new statistics are calculated based on
     * both the current values and the input count structure indicating
     * what has happened since the last statistics update.
     *
     * @param count what has happened since last statistics update.
     * @param secs number of seconds since last statistics update.
     **/
    void Update(FNET_StatCounters *count, double secs);

    /**
     * Invoking this method will generate a log message of type
     * FNET_INFO showing the values held by this object.
     **/
    void Log();
};

