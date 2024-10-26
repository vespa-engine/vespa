// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/searchlib/fef/ranking_assets_repo.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search::fef;
using namespace vespalib::eval;

class DoubleConstantValue : public ConstantValue {
private:
    DoubleValue _value;
    ValueType _type;

public:
    explicit DoubleConstantValue(double value_) : _value(value_), _type(ValueType::double_type()) {}
    const ValueType &type() const override { return _type; }
    const Value &value() const override { return _value; }
};

class MyConstantValueFactory : public ConstantValueFactory {
private:
    using Key = std::pair<std::string, std::string>;
    using Map = std::map<Key, double>;
    Map _map;

public:
    MyConstantValueFactory() : _map() {}
    void add(const std::string &path, const std::string &type, double value) {
        _map.insert(std::make_pair(std::make_pair(path, type), value));
    }
    ConstantValue::UP create(const std::string &path, const std::string &type) const override {
        auto itr = _map.find(std::make_pair(path, type));
        if (itr != _map.end()) {
            return std::make_unique<DoubleConstantValue>(itr->second);
        }
        return std::make_unique<BadConstantValue>();
    }
};

namespace {

std::shared_ptr<RankingConstants> make_ranking_constants() {
    RankingConstants::Vector constants;
    constants.emplace_back("foo", "double", "path_1");
    constants.emplace_back("bar", "double", "path_3");
    return std::make_shared<RankingConstants>(constants);
}

}

class ConstantValueRepoTest : public ::testing::Test {
protected:
    MyConstantValueFactory factory;
    RankingAssetsRepo repo;
    ConstantValueRepoTest();
    ~ConstantValueRepoTest() override;
};

ConstantValueRepoTest::ConstantValueRepoTest()
    : ::testing::Test(),
      factory(),
      repo(factory, make_ranking_constants(), {}, {})
{
    factory.add("path_1", "double", 3);
    factory.add("path_2", "double", 5);
}

ConstantValueRepoTest::~ConstantValueRepoTest() = default;

TEST_F(ConstantValueRepoTest, require_that_constant_value_can_be_retrieved_from_repo)
{
    EXPECT_EQ(3.0, repo.getConstant("foo")->value().as_double());
}

TEST_F(ConstantValueRepoTest, require_that_non_existing_constant_value_in_repo_returns_nullptr)
{
    EXPECT_TRUE(repo.getConstant("none").get() == nullptr);
}

TEST_F(ConstantValueRepoTest, require_that_non_existing_constant_value_in_factory_returns_bad_constant)
{
    EXPECT_TRUE(repo.getConstant("bar")->type().is_error());
}

GTEST_MAIN_RUN_ALL_TESTS()
