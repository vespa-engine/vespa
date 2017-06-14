// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/app.h>
#include <vespa/fastos/file.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/fastlib/util/base64.h>

class Base64Test : public FastOS_Application {
public:
    int Main() override;
};

int
Base64Test::Main(void) {

    char buffer0[1024]; // Original text.
    char buffer1[1024]; // Encoded text.
    char buffer2[1024]; // Decoded text.

    sprintf(buffer0, "Hello, world! This is a test. 123.");

    int length0 = strlen(buffer0);
    int length1 = Fast_Base64::Encode(buffer0, length0, buffer1);

    assert(length1 != -1);

    int length2 = Fast_Base64::Decode(buffer1, length1, buffer2);

    assert(length2 != -1);
    assert(length2 == length0);
    assert(0 == strncmp(buffer0, buffer2, length0));

    printf("Original = '%.*s'\n", length0, buffer0);
    printf("Encoded  = '%.*s'\n", length1, buffer1);
    printf("Decoded  = '%.*s'\n", length2, buffer2);


    // Big file test
    vespalib::string filename("base64test");

    if (_argc > 1) {
        filename.assign(_argv[1]);
    }

    FastOS_StatInfo statInfo;
    int filesize = 0;
    if (FastOS_File::Stat(filename.c_str(), &statInfo)) {
        filesize = statInfo._size;
    } else {
        printf("FAILURE: Could not stat file %s\n", filename.c_str());
        exit(1);
    }

    FastOS_File testFile;
    if (!testFile.OpenReadOnly(filename.c_str())) {
        printf ("FAILURE: Could not open file %s for reading\n", filename.c_str());
        exit(1);
    }


    auto unencoded = std::make_unique<char[]>(filesize);
    auto encoded = std::make_unique<char[]>(filesize * 2);
    auto decoded = std::make_unique<char[]>(filesize + 1);
    testFile.ReadBuf(unencoded.get(), filesize);

    int encLen = Fast_Base64::Encode(unencoded.get(), filesize, encoded.get());
    if (encLen == -1) {
        printf("FAILURE: Encoding failed\n");
        exit(1);
    }

    // encLen is including the trailing '\0' byte, so subtract one
    int decLen = Fast_Base64::Decode(encoded.get(), encLen - 1, decoded.get());
    if (decLen == -1) {
        printf("FAILURE: Decoding failed\n");
        exit(1);
    }

    if (filesize != decLen) {
        printf("FAILURE: decoded length != original filesize, filesize = %d, decLen = %d\n", filesize, decLen);
        exit(1);
    }
    char *uencP = unencoded.get();
    char *decP = decoded.get();
    for (int i = 0; i < filesize; i++) {
        if (*uencP != *decP) {
            printf ("FAILURE: Encode or Decode ERROR! at byte offset %d\n", i);
            exit(1);
        }
        uencP++;
        decP++;
    }
    printf("SUCCESS: Encode/decode OK\n");

    return 0;

}

FASTOS_MAIN(Base64Test)
