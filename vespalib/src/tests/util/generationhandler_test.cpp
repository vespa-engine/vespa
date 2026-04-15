// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <deque>

namespace vespalib {

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
    EXPECT_EQ(Generation(0), gh.getCurrentGeneration());
    EXPECT_EQ(Generation(0u), gh.get_oldest_used_generation());
    gh.incGeneration();
    EXPECT_EQ(Generation(1u), gh.getCurrentGeneration());
    EXPECT_EQ(Generation(1u), gh.get_oldest_used_generation());
}

TEST_F(GenerationHandlerTest, require_that_readers_can_take_guards)
{
    EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(0)));
    {
        GenerationGuard g1 = gh.takeGuard();
        EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(0)));
        {
            GenerationGuard g2 = gh.takeGuard();
            EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
            gh.incGeneration();
            {
                GenerationGuard g3 = gh.takeGuard();
                EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
                EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(1)));
                EXPECT_EQ(3u, gh.getGenerationRefCount());
            }
            EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
            EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
            gh.incGeneration();
            {
                GenerationGuard g3 = gh.takeGuard();
                EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
                EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
                EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(2)));
            }
            EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
            EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
            EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(2)));
        }
        EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(0)));
        EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
        EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(2)));
    }
    EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(0)));
    EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
    EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(2)));
}

TEST_F(GenerationHandlerTest, require_that_guards_can_be_copied)
{
    GenerationGuard g1 = gh.takeGuard();
    EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(0)));
    GenerationGuard g2(g1);
    EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
    gh.incGeneration();
    GenerationGuard g3 = gh.takeGuard();
    EXPECT_EQ(2u, gh.getGenerationRefCount(Generation(0)));
    EXPECT_EQ(1u, gh.getGenerationRefCount(Generation(1)));
    g3 = g2;
    EXPECT_EQ(3u, gh.getGenerationRefCount(Generation(0)));
    EXPECT_EQ(0u, gh.getGenerationRefCount(Generation(1)));
}

TEST_F(GenerationHandlerTest, require_that_the_first_used_generation_is_correct)
{
    EXPECT_EQ(Generation(0u), gh.get_oldest_used_generation());
    gh.incGeneration();
    EXPECT_EQ(Generation(1u), gh.get_oldest_used_generation());
    {
        GenerationGuard g1 = gh.takeGuard();
        gh.incGeneration();
        EXPECT_EQ(1u, gh.getGenerationRefCount());
        EXPECT_EQ(Generation(1u), gh.get_oldest_used_generation());
    }
    EXPECT_EQ(Generation(1u), gh.get_oldest_used_generation());
    gh.update_oldest_used_generation();	// Only writer should call this
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    EXPECT_EQ(Generation(2u), gh.get_oldest_used_generation());
    {
        GenerationGuard g1 = gh.takeGuard();
        gh.incGeneration();
        gh.incGeneration();
        EXPECT_EQ(1u, gh.getGenerationRefCount());
        EXPECT_EQ(Generation(2u), gh.get_oldest_used_generation());
        {
            GenerationGuard g2 = gh.takeGuard();
            EXPECT_EQ(Generation(2u), gh.get_oldest_used_generation());
        }
    }
    EXPECT_EQ(Generation(2u), gh.get_oldest_used_generation());
    gh.update_oldest_used_generation();	// Only writer should call this
    EXPECT_EQ(0u, gh.getGenerationRefCount());
    EXPECT_EQ(Generation(4u), gh.get_oldest_used_generation());
}

TEST_F(GenerationHandlerTest, require_that_generation_can_grow_large)
{
    std::deque<GenerationGuard> guards;
    for (size_t i = 0; i < 10000; ++i) {
        EXPECT_EQ(Generation(i), gh.getCurrentGeneration());
        guards.push_back(gh.takeGuard()); // take guard on current generation
        if (i >= 128) {
            EXPECT_EQ(Generation(i - 128), gh.get_oldest_used_generation());
            guards.pop_front();
            EXPECT_EQ(128u, gh.getGenerationRefCount());
        }
        gh.incGeneration();
    }
}

}
