// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/searchcore/proton/matching/constant_value_repo.h>
#include <vespa/eval/eval/value_cache/constant_value.h>

using namespace proton::matching;
using namespace vespalib::eval;

class DoubleConstantValue : public ConstantValue {
private:
    DoubleValue _value;
    ValueType _type;

public:
    DoubleConstantValue(double value_) : _value(value_), _type(ValueType::double_type()) {}
    virtual const ValueType &type() const override { return _type; }
    virtual const Value &value() const override { return _value; }
};

class MyConstantValueFactory : public ConstantValueFactory {
private:
    using Key = std::pair<vespalib::string, vespalib::string>;
    using Map = std::map<Key, double>;
    Map _map;

public:
    MyConstantValueFactory() : _map() {}
    void add(const vespalib::string &path, const vespalib::string &type, double value) {
        _map.insert(std::make_pair(std::make_pair(path, type), value));
    }
    virtual ConstantValue::UP create(const vespalib::string &path, const vespalib::string &type) const override {
        auto itr = _map.find(std::make_pair(path, type));
        if (itr != _map.end()) {
            return std::make_unique<DoubleConstantValue>(itr->second);
        }
        return std::make_unique<BadConstantValue>();
    }
};

struct Fixture {
    MyConstantValueFactory factory;
    ConstantValueRepo repo;
    Fixture()
        : factory(), repo(factory)
    {
        factory.add("path_1", "double", 3);
        factory.add("path_2", "double", 5);
        repo.reconfigure(RankingConstants({{"foo", "double", "path_1"},
                                           {"bar", "double", "path_3"}}));
    }
};

TEST_F("require that constant value can be retrieved from repo", Fixture)
{
    EXPECT_EQUAL(3, f.repo.getConstant("foo")->value().as_double());
}

TEST_F("require that non-existing constant value in repo returns nullptr", Fixture)
{
    EXPECT_TRUE(f.repo.getConstant("none").get() == nullptr);
}

TEST_F("require that non-existing constant value in factory returns bad constant", Fixture)
{
    EXPECT_TRUE(f.repo.getConstant("bar")->type().is_error());
}

TEST_F("require that reconfigure replaces existing constant values in repo", Fixture)
{
    f.repo.reconfigure(RankingConstants({{"bar", "double", "path_3"},
                                         {"baz", "double", "path_2"}}));
    f.factory.add("path_3", "double", 7);
    EXPECT_TRUE(f.repo.getConstant("foo").get() == nullptr);
    EXPECT_EQUAL(7, f.repo.getConstant("bar")->value().as_double());
    EXPECT_EQUAL(5, f.repo.getConstant("baz")->value().as_double());
}

TEST_MAIN() { TEST_RUN_ALL(); }
