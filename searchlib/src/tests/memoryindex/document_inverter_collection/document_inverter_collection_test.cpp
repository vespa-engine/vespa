// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/document_inverter_collection.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_remover.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/memoryindex/i_field_index_collection.h>
#include <vespa/searchlib/memoryindex/word_store.h>
#include <vespa/searchlib/test/memoryindex/mock_field_index_collection.h>
#include <vespa/searchlib/test/memoryindex/ordered_field_index_inserter_backend.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <thread>

#include <vespa/vespalib/gtest/gtest.h>



namespace search::memoryindex {
using document::Document;
using index::FieldLengthCalculator;
using index::Schema;
using vespalib::RetainGuard;
using vespalib::SequencedTaskExecutor;
using vespalib::ISequencedTaskExecutor;

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

struct DocumentInverterCollectionTest : public ::testing::Test {
    Schema _schema;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    WordStore                       _word_store;
    FieldIndexRemover               _remover;
    test::OrderedFieldIndexInserterBackend _inserter_backend;
    FieldLengthCalculator           _calculator;
    test::MockFieldIndexCollection  _fic;
    DocumentInverterContext         _inv_context;
    DocumentInverterCollection      _inv_collection;

    DocumentInverterCollectionTest()
        : _schema(),
          _invertThreads(SequencedTaskExecutor::create(invert_executor, 4)),
          _pushThreads(SequencedTaskExecutor::create(push_executor, 4)),
          _word_store(),
          _remover(_word_store),
          _inserter_backend(),
          _calculator(),
          _fic(_remover, _inserter_backend, _calculator),
          _inv_context(_schema, *_invertThreads, *_pushThreads, _fic),
          _inv_collection(_inv_context, 10)
    {
    }

};

TEST_F(DocumentInverterCollectionTest, idle_inverter_is_reused)
{
    auto& active = _inv_collection.get_active_inverter();
    for (uint32_t i = 0; i < 4; ++i) {
        _inv_collection.switch_active_inverter();
        EXPECT_EQ(&active, &_inv_collection.get_active_inverter());
    }
    EXPECT_EQ(1u, _inv_collection.get_num_inverters());
}

TEST_F(DocumentInverterCollectionTest, busy_inverter_is_not_reused)
{
    auto& active = _inv_collection.get_active_inverter();
    auto retain = std::make_shared<RetainGuard>(active.get_ref_count());
    _inv_collection.switch_active_inverter();
    EXPECT_NE(&active, &_inv_collection.get_active_inverter());
    EXPECT_EQ(2u, _inv_collection.get_num_inverters());
}

TEST_F(DocumentInverterCollectionTest, number_of_inverters_is_limited_by_max)
{
    for (uint32_t i = 0; i < 50; ++i) {
        auto& active = _inv_collection.get_active_inverter();
        auto retain = std::make_shared<RetainGuard>(active.get_ref_count());
        _pushThreads->execute(i, [retain(std::move(retain))] () { std::this_thread::sleep_for(10ms); });
        _inv_collection.switch_active_inverter();
    }
    EXPECT_LE(4u, _inv_collection.get_num_inverters());
    EXPECT_GE(_inv_collection.get_max_inverters(), _inv_collection.get_num_inverters());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
