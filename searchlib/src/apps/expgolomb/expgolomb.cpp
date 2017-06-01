// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/app.h>
#include <vespa/searchlib/bitcompression/compression.h>
#include <vector>

class ExpGolombApp : public FastOS_Application
{
    void usage();
    int testExpGolomb64(int kValue);
    int testExpGolomb64le(int kValue);
    int Main() override;
};



void
ExpGolombApp::usage()
{
    printf("Usage: expgolomb testeg64 <kValue>]\n");
    fflush(stdout);
}


int
ExpGolombApp::testExpGolomb64(int kValue)
{
    std::vector<uint64_t> myrand;
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        myrand.push_back(rval);
    }
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        uint32_t bits = (rand() & 63);
        rval &= ((UINT64_C(1) << bits) - 1);
        myrand.push_back(rval);
    }
    typedef search::bitcompression::EncodeContext64BE EC;

    EC e;
    search::ComprFileWriteContext wc(e);
    wc.allocComprBuf(32768, 32768);
    e.setupWrite(wc);

    int rsize = myrand.size();
    for (int i = 0; i < rsize; ++i) {
        e.encodeExpGolomb(myrand[i], kValue);
        if (e._valI >= e._valE)
            wc.writeComprBuffer(false);
    }
    e.flush();

    UC64_DECODECONTEXT(o);
    unsigned int length;
    uint64_t val64;
    UC64BE_SETUPBITS_NS(o, static_cast<const uint64_t *>(wc._comprBuf), 0, EC);

    bool failure = false;
    for (int i = 0; i < rsize; ++i) {
        UC64BE_DECODEEXPGOLOMB(oVal, oCompr, oPreRead, oCacheInt,
                               kValue, EC);
        if (val64 != myrand[i]) {
            printf("FAILURE: TestExpGolomb64, val64=%"
                   PRIu64 ", myrand[%d]=%" PRIu64 "\n",
                   val64, i, myrand[i]);
            failure = true;
        }
    }
    if (!failure)
        printf("SUCCESS: TestExpGolomb64\n");
    return failure ? 1 : 0;
}

int
ExpGolombApp::testExpGolomb64le(int kValue)
{
    std::vector<uint64_t> myrand;
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        myrand.push_back(rval);
    }
    for (int i = 0; i < 10000; ++i) {
        uint64_t rval = rand();
        rval <<= 30;
        rval |= rand();
        uint32_t bits = (rand() & 63);
        rval &= ((UINT64_C(1) << bits) - 1);
        myrand.push_back(rval);
    }
    typedef search::bitcompression::EncodeContext64LE EC;

    EC e;
    search::ComprFileWriteContext wc(e);
    wc.allocComprBuf(32768, 32768);
    e.setupWrite(wc);

    int rsize = myrand.size();
    for (int i = 0; i < rsize; ++i) {
        e.encodeExpGolomb(myrand[i], kValue);
        if (e._valI >= e._valE)
            wc.writeComprBuffer(false);
    }
    e.flush();

    UC64_DECODECONTEXT(o);
    unsigned int length;
    uint64_t val64;
    UC64LE_SETUPBITS_NS(o, static_cast<const uint64_t *>(wc._comprBuf), 0, EC);

    bool failure = false;
    for (int i = 0; i < rsize; ++i) {
        UC64LE_DECODEEXPGOLOMB(oVal, oCompr, oPreRead, oCacheInt,
                               kValue, EC);
        if (val64 != myrand[i]) {
            printf("FAILURE: TestExpGolomb64le, val64=%"
                   PRIu64 ", myrand[%d]=%" PRIu64 "\n",
                   val64, i, myrand[i]);
            failure = true;
        }
    }
    if (!failure)
        printf("SUCCESS: TestExpGolomb64le\n");
    return failure ? 1 : 0;
}


int
ExpGolombApp::Main()
{
    printf("Hello world\n");
    if (_argc >= 2) {
        if (strcmp(_argv[1], "testeg64") == 0) {
            if (_argc < 3) {
                fprintf(stderr, "Too few arguments\n");
                usage();
                return 1;
            }
            return testExpGolomb64(atoi(_argv[2]));
        } else if (strcmp(_argv[1], "testeg64le") == 0) {
            if (_argc < 3) {
                fprintf(stderr, "Too few arguments\n");
                usage();
                return 1;
            }
            return testExpGolomb64le(atoi(_argv[2]));
        } else {
            fprintf(stderr, "Wrong arguments\n");
            usage();
            return 1;
        }
    } else {
        fprintf(stderr, "Too few arguments\n");
        usage();
        return 1;
    }
    return 0;
}

FASTOS_MAIN(ExpGolombApp);


