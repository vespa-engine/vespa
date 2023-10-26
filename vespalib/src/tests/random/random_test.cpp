// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/random.h>

using namespace vespalib;


class Test : public vespalib::TestApp
{
public:
    int getFive() { return 5; }
    void testJavaCompatibility();
    void testFloatingPoint();
    void testNormalDistribution();
    int Main() override;
};

bool eqD(double a, double b) {
    if (a == b) return true;
    printf("is %f approx equal to %f ?\n", a, b);
    if ((a + 1.0e-9) > b && b > (a - 1.0e-9)) return true;
    return false;
}

void
Test::testJavaCompatibility()
{
    RandomGen rnd(1);

    EXPECT_TRUE(rnd.nextInt32() == -1155869325);
    EXPECT_TRUE(rnd.nextInt32() == 431529176);
    EXPECT_TRUE(rnd.nextInt32() == 1761283695);
    EXPECT_TRUE(rnd.nextInt32() == 1749940626);
    EXPECT_TRUE(rnd.nextInt32() == 892128508);
    EXPECT_TRUE(rnd.nextInt32() == 155629808);
    EXPECT_TRUE(rnd.nextInt32() == 1429008869);
    EXPECT_TRUE(rnd.nextInt32() == -1465154083);
    EXPECT_TRUE(rnd.nextInt32() == -138487339);
    EXPECT_TRUE(rnd.nextInt32() == -1242363800);
    EXPECT_TRUE(rnd.nextInt32() == 26273138);
    EXPECT_TRUE(rnd.nextInt32() == 655996946);

    rnd.setSeed(1);
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.7308781907032909));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.41008081149220166));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.20771484130971707));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.3327170559595112));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.9677559094241207));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.006117182265761301));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.9637047970232077));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.9398653887819098));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.9471949176631939));
    EXPECT_TRUE(eqD(rnd.nextDouble(), 0.9370821488959696));

    RandomGen rnd2(-1);
    EXPECT_TRUE(rnd2.nextInt32() == 1155099827);
    EXPECT_TRUE(rnd2.nextInt32() == 1887904451);
    EXPECT_TRUE(rnd2.nextInt32() == 52699159);

    rnd2.setSeed(-1);
    EXPECT_TRUE(rnd2.nextInt32() == 1155099827);
    EXPECT_TRUE(rnd2.nextInt32() == 1887904451);
    EXPECT_TRUE(rnd2.nextInt32() == 52699159);
}

void
Test::testFloatingPoint()
{
    RandomGen rnd;

    int buckets[100];
    for (int b = 0; b < 100; b++) {
        buckets[b] = 0;
    }
    for (int i = 0; i < 100000; i++) {
        double foo = rnd.nextDouble() * 100.0;
        int b = (int)foo;
        EXPECT_TRUE(b >= 0);
        EXPECT_TRUE(b < 100);
        if (b >= 0 && b < 100) ++buckets[b];
    }
    for (int b = 0; b < 100; b++) {
        // note that it's *possible* for this to fail:
        EXPECT_TRUE(buckets[b] > 800);
        EXPECT_TRUE(buckets[b] < 1200);
        // printf("bucket[%d] = %d\n", b, buckets[b]);
    }
}

void
Test::testNormalDistribution()
{
    RandomGen rnd;

    int buckets[101];
    for (int b = 0; b < 101; b++) {
        buckets[b] = 0;
    }

    const int sum = 10000000;
    int oor = 0;
    for (int i = 0; i < sum; i++) {
        double foo = rnd.nextNormal(50, 13);
        int idx = (int)(foo+0.5);
        if (foo < 0) {
            ++oor;
            idx = 0;
        }
        if (foo > 100) {
            ++oor;
            idx = 100;
        }
        buckets[idx]++;
    }
    EXPECT_TRUE(oor < 0.001 * sum);
    printf("out of range of normal distribution: %d / %d\n", oor, sum);

    printf("histogram in form:\nbucket\tnum\n>>> begin >>>\n");
    for (int b = 0; b < 101; b++) {
        printf("%d\t%d\n", b, buckets[b]);
    }
    printf("<<< end histogram <<<\n");

    EXPECT_TRUE(buckets[50] > buckets[45]);
    EXPECT_TRUE(buckets[45] > buckets[40]);
    EXPECT_TRUE(buckets[40] > buckets[35]);
    EXPECT_TRUE(buckets[35] > buckets[30]);
    EXPECT_TRUE(buckets[30] > buckets[25]);
    EXPECT_TRUE(buckets[25] > buckets[20]);
    EXPECT_TRUE(buckets[20] > buckets[15]);
    EXPECT_TRUE(buckets[15] > buckets[10]);
    EXPECT_TRUE(buckets[10] > buckets[5]);
    EXPECT_TRUE(buckets[5]  > buckets[1]);

    EXPECT_TRUE(buckets[50] > buckets[55]);
    EXPECT_TRUE(buckets[55] > buckets[60]);
    EXPECT_TRUE(buckets[60] > buckets[65]);
    EXPECT_TRUE(buckets[65] > buckets[70]);
    EXPECT_TRUE(buckets[70] > buckets[75]);
    EXPECT_TRUE(buckets[75] > buckets[80]);
    EXPECT_TRUE(buckets[80] > buckets[85]);
    EXPECT_TRUE(buckets[85] > buckets[90]);
    EXPECT_TRUE(buckets[90] > buckets[95]);
    EXPECT_TRUE(buckets[95] > buckets[99]);

    // not too fat tails:
    EXPECT_TRUE(buckets[10] > buckets[0]);
    EXPECT_TRUE(buckets[90] > buckets[100]);
}

int
Test::Main()
{
    TEST_INIT("random_test");
    testJavaCompatibility();
    TEST_FLUSH();
    testFloatingPoint();
    TEST_FLUSH();
    testNormalDistribution();
    TEST_DONE();
}

TEST_APPHOOK(Test)
