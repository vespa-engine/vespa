// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/document/bucket/bucket.h>
#include <gtest/gtest.h>

namespace document {

struct Hex {
    BucketId::Type val;

    Hex(BucketId::Type v) : val(v) {}
    bool operator==(const Hex& h) const { return val == h.val; }
};

inline std::ostream& operator<<(std::ostream& out, const Hex& h) {
    out << std::hex << h.val << std::dec;
    return out;
}

TEST(BucketTest, testBucketId)
{
    // Test empty (invalid) buckets
    BucketId id1;
    BucketId id2;
    EXPECT_EQ(id1, id2);
    EXPECT_TRUE(!(id1 < id2) && !(id2 < id1));
    EXPECT_EQ(Hex(0), Hex(id1.getId()));
    EXPECT_EQ(Hex(0), Hex(id1.getRawId()));
    EXPECT_EQ(vespalib::string("BucketId(0x0000000000000000)"),
                         id1.toString());
    EXPECT_EQ(0u, id1.getUsedBits());

    // Test bucket with a value
    id2 = BucketId((BucketId::Type(16) << 58) | 0x123);
    EXPECT_TRUE(id1 != id2);
    EXPECT_TRUE((id1 < id2) && !(id2 < id1));
    EXPECT_EQ(Hex(0x4000000000000123ull), Hex(id2.getId()));
    EXPECT_EQ(Hex(0x4000000000000123ull), Hex(id2.getRawId()));
    EXPECT_EQ(vespalib::string("BucketId(0x4000000000000123)"),
              id2.toString());
    EXPECT_EQ(16u, id2.getUsedBits());

    // Test copy constructor and operator=
    BucketId id3(id2);
    EXPECT_EQ(id2, id3);
    id3 = id1;
    EXPECT_TRUE(!(id2 == id3));
    id3 = id2;
    EXPECT_EQ(id2, id3);
}

TEST(BucketTest, testGetBit)
{
    for (uint32_t i = 0; i < 58; ++i) {
        EXPECT_EQ(0, (int)document::BucketId(16, 0).getBit(i));
    }

    for (uint32_t i = 0; i < 4; ++i) {
        EXPECT_EQ(0, (int)document::BucketId(16, 16).getBit(i));
    }

    EXPECT_EQ(1, (int)document::BucketId(16, 16).getBit(4));

    for (uint32_t i = 5; i < 59; ++i) {
        EXPECT_EQ(0, (int)document::BucketId(16, 16).getBit(i));
    }

    EXPECT_EQ(0, (int)document::BucketId(17, 0x0ffff).getBit(16));

    for (uint32_t i = 0; i < 16; ++i) {
        EXPECT_EQ(1, (int)document::BucketId(17, 0x0ffff).getBit(i));
    }
}

TEST(BucketTest, testBucketGeneration)
{
    BucketIdFactory factory;
    DocumentId doc1("id:ns:type::1");
    DocumentId doc2("id:ns2:type::1");
    DocumentId doc3("id:ns:type2::1");
    DocumentId doc4("id:ns:type::2");
    DocumentId userDoc1("id:ns:mytype:n=18:spec");
    DocumentId userDoc2("id:ns2:mytype:n=18:spec2");
    DocumentId userDoc3("id:ns:mytype:n=19:spec");
    DocumentId groupDoc1("id:ns:mytype:g=yahoo.com:spec");
    DocumentId groupDoc2("id:ns2:mytype:g=yahoo.com:spec2");
    DocumentId groupDoc3("id:ns:mytype:g=yahoo:spec");

    BucketId docBucket1 = factory.getBucketId(doc1);
    BucketId docBucket2 = factory.getBucketId(doc2);
    BucketId docBucket3 = factory.getBucketId(doc3);
    BucketId docBucket4 = factory.getBucketId(doc4);
    BucketId userDocBucket1(factory.getBucketId(userDoc1));
    BucketId userDocBucket2(factory.getBucketId(userDoc2));
    BucketId userDocBucket3(factory.getBucketId(userDoc3));
    BucketId groupDocBucket1(factory.getBucketId(groupDoc1));
    BucketId groupDocBucket2(factory.getBucketId(groupDoc2));
    BucketId groupDocBucket3(factory.getBucketId(groupDoc3));

    EXPECT_EQ(Hex(0xeb3089a300000012ull), Hex(userDocBucket1.getRawId()));
    EXPECT_EQ(Hex(0xe87e777b00000012ull), Hex(userDocBucket2.getRawId()));
    EXPECT_EQ(Hex(0xe80d16fc00000013ull), Hex(userDocBucket3.getRawId()));

    userDocBucket1.setUsedBits(16);
    EXPECT_EQ(Hex(0x4000000000000012ull), Hex(userDocBucket1.getId()));
    userDocBucket2.setUsedBits(16);
    EXPECT_EQ(Hex(0x4000000000000012ull), Hex(userDocBucket2.getId()));
    userDocBucket3.setUsedBits(16);
    EXPECT_EQ(Hex(0x4000000000000013ull), Hex(userDocBucket3.getId()));

    EXPECT_EQ(Hex(0xeb82f2be9a1acd50ull), Hex(groupDocBucket1.getRawId()));
    EXPECT_EQ(Hex(0xebff6e379a1acd50ull), Hex(groupDocBucket2.getRawId()));
    EXPECT_EQ(Hex(0xe91b9600afe81f24ull), Hex(groupDocBucket3.getRawId()));

    groupDocBucket1.setUsedBits(16);
    EXPECT_EQ(Hex(0x400000000000cd50ull), Hex(groupDocBucket1.getId()));
    groupDocBucket2.setUsedBits(16);
    EXPECT_EQ(Hex(0x400000000000cd50ull), Hex(groupDocBucket2.getId()));
    groupDocBucket3.setUsedBits(16);
    EXPECT_EQ(Hex(0x4000000000001f24ull), Hex(groupDocBucket3.getId()));

    EXPECT_EQ(Hex(0xe9362c053842cac4ull), Hex(docBucket1.getRawId()));
    EXPECT_EQ(Hex(0xe960b5773842cac4ull), Hex(docBucket2.getRawId()));
    EXPECT_EQ(Hex(0xe8daaf763842cac4ull), Hex(docBucket3.getRawId()));
    EXPECT_EQ(Hex(0xeb5016ab8d721ec8ull), Hex(docBucket4.getRawId()));

    docBucket1.setUsedBits(16);
    EXPECT_EQ(Hex(0x400000000000cac4ull), Hex(docBucket1.getId()));
    docBucket2.setUsedBits(16);
    EXPECT_EQ(Hex(0x400000000000cac4ull), Hex(docBucket2.getId()));
    docBucket3.setUsedBits(16);
    EXPECT_EQ(Hex(0x400000000000cac4ull), Hex(docBucket3.getId()));
    docBucket4.setUsedBits(16);
    EXPECT_EQ(Hex(0x4000000000001ec8ull), Hex(docBucket4.getId()));
}

TEST(BucketTest, testBucketSerialization)
{
    BucketIdFactory factory;
    DocumentId doc("id:ns:test::1");
    BucketId bucket(factory.getBucketId(doc));

    std::ostringstream ost;
    ost << bucket.getRawId();
    EXPECT_EQ(std::string("16910189189155441348"), ost.str());

    BucketId::Type id;
    std::istringstream ist(ost.str());
    ist >> id;
    BucketId bucket2(id);

    EXPECT_EQ(bucket, bucket2);
}

TEST(BucketTest, testReverseBucket)
{
    {
        BucketId id(0x3000000000000012ull);
        EXPECT_EQ(Hex(0x480000000000000cull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }

    {
        BucketId id(0x4000000000000012ull);
        EXPECT_EQ(Hex(0x4800000000000010ull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }

    {
        BucketId id(0x600000000000ffffull);
        EXPECT_EQ(Hex(0xffff000000000018ull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }

    {
        BucketId id(0x540000000001ffffull);
        EXPECT_EQ(Hex(0xffff800000000015ull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }

    {
        BucketId id(0xa80000000003ffffull);
        EXPECT_EQ(Hex(0xffffc0000000002aull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }

    {
        BucketId id(0xbc0000000007ffffull);
        EXPECT_EQ(Hex(0xffffe0000000002full), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }
    {
        BucketId id(0xcc0000000002ffffull);
        EXPECT_EQ(Hex(0xffff400000000033ull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }
    {
        BucketId id(0xebffffffffffffffull);
        EXPECT_EQ(Hex(0xfffffffffffffffaull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }
    {
        BucketId id(0xeaaaaaaaaaaaaaaaull);
        EXPECT_EQ(Hex(0x555555555555557aull), Hex(id.toKey()));
        EXPECT_EQ(Hex(id.getId()), Hex(document::BucketId::keyToBucketId(id.stripUnused().toKey())));
    }
}

TEST(BucketTest, testContains)
{
    BucketId id(18, 0x123456789ULL);
    EXPECT_TRUE(id.contains(BucketId(20, 0x123456789ULL)));
    EXPECT_TRUE(id.contains(BucketId(18, 0x888f56789ULL)));
    EXPECT_TRUE(id.contains(BucketId(24, 0x888456789ULL)));
    EXPECT_TRUE(!id.contains(BucketId(24, 0x888886789ULL)));
    EXPECT_TRUE(!id.contains(BucketId(16, 0x123456789ULL)));
}

TEST(BucketTest, testToString)
{
    BucketSpace bucketSpace(0x123450006789ULL);
    EXPECT_EQ(vespalib::string("BucketSpace(0x0000123450006789)"), bucketSpace.toString());
    Bucket bucket(bucketSpace, BucketId(0x123456789ULL));
    EXPECT_EQ(
            vespalib::string("Bucket(BucketSpace(0x0000123450006789), BucketId(0x0000000123456789))"),
            bucket.toString());
}

TEST(BucketTest, testOperators)
{
    EXPECT_TRUE(BucketSpace(0x1) == BucketSpace(0x1));
    EXPECT_TRUE(BucketSpace(0x1) != BucketSpace(0x2));
    EXPECT_TRUE(BucketSpace(0x1) < BucketSpace(0x2));

    EXPECT_TRUE(Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)) ==
                Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)));
    EXPECT_TRUE(Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)) !=
                Bucket(BucketSpace(0x2), BucketId(0x123456789ULL)));
    EXPECT_TRUE(Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)) !=
                Bucket(BucketSpace(0x1), BucketId(0x987654321ULL)));
    EXPECT_TRUE(Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)) <
                Bucket(BucketSpace(0x1), BucketId(0x987654321ULL)));
    EXPECT_TRUE(Bucket(BucketSpace(0x1), BucketId(0x123456789ULL)) <
                Bucket(BucketSpace(0x2), BucketId(0x123456789ULL)));
}

} // document
