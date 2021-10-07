// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <deque>

namespace vespalib {

typedef GenerationHandler::Guard GenGuard;

class Test : public vespalib::TestApp {
private:
    void requireThatGenerationCanBeIncreased();
    void requireThatReadersCanTakeGuards();
    void requireThatGuardsCanBeCopied();
    void requireThatTheFirstUsedGenerationIsCorrect();
    void requireThatGenerationCanGrowLarge();
public:
    int Main() override;
};

void
Test::requireThatGenerationCanBeIncreased()
{
    GenerationHandler gh;
    EXPECT_EQUAL(0u, gh.getCurrentGeneration());
    EXPECT_EQUAL(0u, gh.getFirstUsedGeneration());
    gh.incGeneration();
    EXPECT_EQUAL(1u, gh.getCurrentGeneration());
    EXPECT_EQUAL(1u, gh.getFirstUsedGeneration());
}

void
Test::requireThatReadersCanTakeGuards()
{
    GenerationHandler gh;
    EXPECT_EQUAL(0u, gh.getGenerationRefCount(0));
    {
        GenGuard g1 = gh.takeGuard();
        EXPECT_EQUAL(1u, gh.getGenerationRefCount(0));
        {
            GenGuard g2 = gh.takeGuard();
            EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
            gh.incGeneration();
            {
                GenGuard g3 = gh.takeGuard();
                EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
                EXPECT_EQUAL(1u, gh.getGenerationRefCount(1));
                EXPECT_EQUAL(3u, gh.getGenerationRefCount());
            }
            EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
            EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
            gh.incGeneration();
            {
                GenGuard g3 = gh.takeGuard();
                EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
                EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
                EXPECT_EQUAL(1u, gh.getGenerationRefCount(2));
            }
            EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
            EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
            EXPECT_EQUAL(0u, gh.getGenerationRefCount(2));
        }
        EXPECT_EQUAL(1u, gh.getGenerationRefCount(0));
        EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
        EXPECT_EQUAL(0u, gh.getGenerationRefCount(2));
    }
    EXPECT_EQUAL(0u, gh.getGenerationRefCount(0));
    EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
    EXPECT_EQUAL(0u, gh.getGenerationRefCount(2));
}

void
Test::requireThatGuardsCanBeCopied()
{
    GenerationHandler gh;
    GenGuard g1 = gh.takeGuard();
    EXPECT_EQUAL(1u, gh.getGenerationRefCount(0));
    GenGuard g2(g1);
    EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
    gh.incGeneration();
    GenGuard g3 = gh.takeGuard();
    EXPECT_EQUAL(2u, gh.getGenerationRefCount(0));
    EXPECT_EQUAL(1u, gh.getGenerationRefCount(1));
    g3 = g2;
    EXPECT_EQUAL(3u, gh.getGenerationRefCount(0));
    EXPECT_EQUAL(0u, gh.getGenerationRefCount(1));
}

void
Test::requireThatTheFirstUsedGenerationIsCorrect()
{
    GenerationHandler gh;
    EXPECT_EQUAL(0u, gh.getFirstUsedGeneration());
    gh.incGeneration();
    EXPECT_EQUAL(1u, gh.getFirstUsedGeneration());
    {
        GenGuard g1 = gh.takeGuard();
        gh.incGeneration();
        EXPECT_EQUAL(true, gh.hasReaders());
        EXPECT_EQUAL(1u, gh.getFirstUsedGeneration());
    }
    EXPECT_EQUAL(1u, gh.getFirstUsedGeneration());
    gh.updateFirstUsedGeneration();	// Only writer should call this
    EXPECT_EQUAL(false, gh.hasReaders());
    EXPECT_EQUAL(2u, gh.getFirstUsedGeneration());
    {
        GenGuard g1 = gh.takeGuard();
        gh.incGeneration();
        gh.incGeneration();
        EXPECT_EQUAL(true, gh.hasReaders());
        EXPECT_EQUAL(2u, gh.getFirstUsedGeneration());
        {
            GenGuard g2 = gh.takeGuard();
            EXPECT_EQUAL(2u, gh.getFirstUsedGeneration());
        }
    }
    EXPECT_EQUAL(2u, gh.getFirstUsedGeneration());
    gh.updateFirstUsedGeneration();	// Only writer should call this
    EXPECT_EQUAL(false, gh.hasReaders());
    EXPECT_EQUAL(4u, gh.getFirstUsedGeneration());
}

void
Test::requireThatGenerationCanGrowLarge()
{
    GenerationHandler gh;
    std::deque<GenGuard> guards;
    for (size_t i = 0; i < 10000; ++i) {
        EXPECT_EQUAL(i, gh.getCurrentGeneration());
        guards.push_back(gh.takeGuard()); // take guard on current generation
        if (i >= 128) {
            EXPECT_EQUAL(i - 128, gh.getFirstUsedGeneration());
            guards.pop_front();
            EXPECT_EQUAL(128u, gh.getGenerationRefCount());
        }
        gh.incGeneration();
    }
}

int
Test::Main()
{
    TEST_INIT("generationhandler_test");

    TEST_DO(requireThatGenerationCanBeIncreased());
    TEST_DO(requireThatReadersCanTakeGuards());
    TEST_DO(requireThatGuardsCanBeCopied());
    TEST_DO(requireThatTheFirstUsedGenerationIsCorrect());
    TEST_DO(requireThatGenerationCanGrowLarge());

    TEST_DONE();
}

}

TEST_APPHOOK(vespalib::Test);
