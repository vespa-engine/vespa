// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/messagebus/blob.h>
#include <vespa/messagebus/blobref.h>

using mbus::Blob;
using mbus::BlobRef;

TEST_SETUP(Test);

Blob makeBlob(const char *txt) {
    Blob b(strlen(txt) + 1);
    strcpy(b.data(), txt);
    return b;
}

BlobRef makeBlobRef(const Blob &b) {
    return BlobRef(b.data(), b.size());
}

Blob returnBlob(Blob b) {
    return b;
}

BlobRef returnBlobRef(BlobRef br) {
    return br;
}

int
Test::Main()
{
    TEST_INIT("blob_test");

    // create a blob
    Blob b = makeBlob("test");
    EXPECT_TRUE(b.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", b.data()) == 0);

    // create a ref to a blob
    BlobRef br = makeBlobRef(b);
    EXPECT_TRUE(br.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", br.data()) == 0);
    EXPECT_TRUE(b.data() == br.data());

    // non-destructive copy of ref
    BlobRef br2 = returnBlobRef(br);
    EXPECT_TRUE(br.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", br.data()) == 0);
    EXPECT_TRUE(b.data() == br.data());
    EXPECT_TRUE(br2.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", br2.data()) == 0);
    EXPECT_TRUE(b.data() == br2.data());

    br = br2;
    EXPECT_TRUE(br.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", br.data()) == 0);
    EXPECT_TRUE(b.data() == br.data());
    EXPECT_TRUE(br2.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", br2.data()) == 0);
    EXPECT_TRUE(b.data() == br2.data());

    // destructive copy of blob
    Blob b2 = returnBlob(std::move(b));
    EXPECT_EQUAL(0u, b.size());
    EXPECT_TRUE(b.data() == 0);
    EXPECT_TRUE(b2.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", b2.data()) == 0);

    b.swap(b2);
    EXPECT_EQUAL(0u, b2.size());
    EXPECT_TRUE(b2.data() == 0);
    EXPECT_TRUE(b.size() == strlen("test") + 1);
    EXPECT_TRUE(strcmp("test", b.data()) == 0);

    TEST_DONE();
}
