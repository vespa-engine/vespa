// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <deque>

namespace vespalib {

using GenGuard = GenerationHandler::Guard;

class GenerationHandlerTest : public ::testing::Test {
protected:
    GenerationHandler gh;
    GenerationHandlerTest();
    ~GenerationHandlerTest() override;
};

GenerationHandlerTest::GenerationHandlerTest()
    : ::testing::Test(),
      gh()
{
}

GenerationHandlerTest::~GenerationHandlerTest() = default;

TEST_F(GenerationHandlerTest, require_that_generation_can_be_increased)
{
    EXPECT_EQ(0u, gh.getCurrentGeneration());
    EXPECT_EQ(0u, gh.get_oldest_used_generation());
    gh.incGeneration();
    EXPECT_EQ(1u, gh.getCurrentGeneration());
    EXPECT_EQ(1u, gh.get_oldest_used_generation());
}

TEST_F(GenerationHandlerTest, require_that_readers_can_take_guards)
{
    EXPECT_EQ(0u, gh.getGenerationRefCount(0));
    {
        GenGuard g1 = gh.takeGuard();
        EXPECT_EQ(1u, gh.getGenerationRefCount(0));
        {
            GenGuard g2 = gh.takeGuard();
            EXPECT_EQ(2u, gh.getGenerationRefCount(0));
            gh.incGeneration();
            {
                GenGuard g3 = gh.takeGuard();
                EXPECT_EQ(2u, gh.getGenerationRefCount(0));
                EXPECT_EQ(1u, gh.getGenerationRefCount(1));
                EXPECT_EQ(3u, gh.getGenerationRefCount());
            }
            EXPECT_EQ(2u, gh.getGenerationRefCount(0));
            EXPECT_EQ(0u, gh.getGenerationRefCount(1));
            gh.incGeneration();
            {
                GenGuard g3 = gh.takeGuard();
                EXPECT_EQ(2u, gh.getGenerationRefCount(0));
                EXPECT_EQ(0u, gh.getGenerationRefCount(1));
                EXPECT_EQ(1u, gh.getGenerationRefCount(2));
            }
            EXPECT_EQ(2u, gh.getGenerationRefCount(0));
            EXPECT_EQ(0u, gh.getGenerationRefCount(1));
            EXPECT_EQ(0u, gh.getGenerationRefCount(2));
        }
        EXPECT_EQ(1u, gh.getGenerationRefCount(0));
        EXPECT_EQ(0u, gh.getGenerationRefCount(1));
        EXPECT_EQ(0u, gh.getGenerationRefCount(2));
    }
    EXPECT_EQ(0u, gh.getGenerationRefCount(0));
    EXPECT_EQ(0u, gh.getGenerationRefCount(1));
    EXPECT_EQ(0u, gh.getGenerationRefCount(2));
}

TEST_F(GenerationHandlerTest, require_that_guards_can_be_copied)
{
    GenGuard g1 = gh.takeGuard();
    EXPECT_EQ(1u, gh.getGenerationRefCount(0));
    GenGuard g2(g1);
    EXPECT_EQ(2u, gh.getGenerationRefCount(0));
    gh.incGeneration();
    GenGuard g3 = gh.takeGuard();
    EXPECT_EQ(2u, gh.getGenerationRefCount(0));
    EXPECT_EQ(1u, gh.getGenerationRefCount(1));
    g3 = g2;
    EXPECT_EQ(3u, gh.getGenerationRefCount(0));
    EXPECT_EQ(0u, gh.getGenerationRefCount(1));
}

TEST_F(GenerationHandlerTest, require_that_the_first_used_generation_is_correct)
{
    EXPECT_EQ(0u, gh.get_oldest_used_generation());
    gh.incGeneration();
    EXPECT_EQ(1u, gh.get_oldest_used_generation());
    {
        GenGuard g1 = gh.takeGuard();
        gh.incGeneration();
        EXPECT_EQ(1u, gh.getGenerationRefCount());
        EXPECT_EQ(1u, gh.get_oldest_used_generation());
    }
    EXPECT_EQ(1u, gh.get_oldest_used_generation());
    gh.update_oldest_used_generation();	// Only writer should call this
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    EXPECT_EQ(2u, gh.get_oldest_used_generation());
    {
        GenGuard g1 = gh.takeGuard();
        gh.incGeneration();
        gh.incGeneration();
        EXPECT_EQ(1u, gh.getGenerationRefCount());
        EXPECT_EQ(2u, gh.get_oldest_used_generation());
        {
            GenGuard g2 = gh.takeGuard();
            EXPECT_EQ(2u, gh.get_oldest_used_generation());
        }
    }
    EXPECT_EQ(2u, gh.get_oldest_used_generation());
    gh.update_oldest_used_generation();	// Only writer should call this
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    EXPECT_EQ(4u, gh.get_oldest_used_generation());
}

TEST_F(GenerationHandlerTest, require_that_generation_can_grow_large)
{
    std::deque<GenGuard> guards;
    for (size_t i = 0; i < 10000; ++i) {
        EXPECT_EQ(i, gh.getCurrentGeneration());
        guards.push_back(gh.takeGuard()); // take guard on current generation
        if (i >= 128) {
            EXPECT_EQ(i - 128, gh.get_oldest_used_generation());
            guards.pop_front();
            EXPECT_EQ(128u, gh.getGenerationRefCount());
        }
        gh.incGeneration();
    }
}

}

GTEST_MAIN_RUN_ALL_TESTS()
