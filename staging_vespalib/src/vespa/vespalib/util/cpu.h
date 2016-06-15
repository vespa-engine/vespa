// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#pragma once

#include <vespa/fastos/fastos.h>

namespace vespalib {

#if defined(__i386__) || defined(__x86_64__)
class X86CpuInfo
{
public:
    bool hasMMX()  const { return hasFeature(23); }
    bool hasSSE()  const { return hasFeature(25); }
    bool hasSSE2() const { return hasFeature(26); }
    bool hasSSE3() const { return hasFeature(32); }
    bool hasCX16() const { return hasFeature(45); }

    bool hasFeature(size_t i) const { return (_cpuFeatures >> i) & 1; }
    bool hasFeature(const char *name) const;
    static void print(FILE * fp);

    /**
     * Use this accessor to get information about the CPU.
     * Only the first access will actually extract data, all
     * subsequent uses are very cheap.
     **/
    static const X86CpuInfo& cpuInfo() {
        if (_CpuInfoSingletonPtr == 0) {
            fillSingleton();
            _CpuInfoSingletonPtr = &_CpuInfoSingleton;
        }
        return *_CpuInfoSingletonPtr;
    }

private:
    struct CpuFeature
    {
        size_t      BitNo;
        const char *Name;
        const char *Description;
        const char *Comment;
    };
    static CpuFeature _CpuFeatureList[];

    static X86CpuInfo _CpuInfoSingleton;
    static X86CpuInfo *_CpuInfoSingletonPtr;
    static void fillSingleton();

    // no outside construction allowed:
    X86CpuInfo();

    uint64_t _cpuFeatures;

public:
    // public data extracted from CPU
    uint32_t mainFeatures;
    uint32_t extendedFeatures;
    uint32_t apicInfo; // brand, CLFLUSH line size, logical / physical, local apic ID
    uint32_t versionInfo; // family, model, stepping

    // add more info later:
    // uint32_t numCpuInfo;

    char cpuName[13];
    uint32_t largestStandardFunction;

};
#endif

/**
 * @brief Atomic instructions class
 *
 * Here are fast handcoded assembly functions for doing some special
 * low level functions that can be carried out very fast by special instructions.
 * Currently only implemented for GCC on i386 and x86_64 platforms.
 **/
class Cpu
{
public:
#if defined(__x86_64__)
    static void cpuid(int op, uint32_t & eax, uint32_t & ebx, uint32_t & ecx, uint32_t & edx) {
        __asm__("cpuid"
                : "=a" (eax),
                "=b" (ebx),
                "=c" (ecx),
                "=d" (edx)
                : "0" (op));
    }
#endif
};

}

