// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
/**
 * This class provides overall information about the FNET
 * implementation.
 **/
class FNET_Info {
public:
    /**
     * Host endian enum. See @ref GetEndian method below.
     **/
    enum { ENDIAN_UNKNOWN, ENDIAN_LITTLE, ENDIAN_BIG };

private:
    static uint32_t _endian;

public:
    /**
     * A static instance of the FNET_Info class is used to ensure that
     * this method is run (once) on application startup. It performs
     * some probing to obtain information about the host.
     **/
    FNET_Info();

    /**
     * @return true if we have support for threads
     **/
    static bool HasThreads() { return true; }

    /**
     * @return the host endian (unknown/little/big)
     **/
    static uint32_t GetEndian() { return _endian; }

    /**
     * This method may be used to obtain a string describing the
     * FNET version.
     *
     * @return a string indicating the FNET version.
     **/
    static const char* GetFNETVersion();

    /**
     * This method is deprecated.  Use the FNET_Info::LogInfo method
     * instead.
     **/
    static void PrintInfo();

    /**
     * Invoking this method logs various information about FNET.
     **/
    static void LogInfo();
};
