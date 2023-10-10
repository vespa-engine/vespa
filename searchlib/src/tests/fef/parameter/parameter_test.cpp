// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("parameter_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/fef/parametervalidator.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/fef/test/indexenvironmentbuilder.h>

using namespace search::fef::test;
using CollectionType = search::fef::FieldInfo::CollectionType;
using DataType = search::fef::FieldInfo::DataType;

namespace search {
namespace fef {

class StringList : public std::vector<vespalib::string> {
public:
    StringList & add(const vespalib::string & str) { push_back(str); return *this; }
};

class ParameterTest : public vespalib::TestApp {
private:
    using PDS = ParameterDescriptions;
    using PT = ParameterType;
    using P = Parameter;
    using SL = StringList;
    using PVR = ParameterValidator::Result;

    bool assertParameter(const Parameter & exp, const Parameter & act);
    bool validate(const IIndexEnvironment & env,
                  const std::vector<vespalib::string> & params,
                  const ParameterDescriptions & descs);
    bool validate(const IIndexEnvironment & env,
                  const std::vector<vespalib::string> & params,
                  const ParameterDescriptions & descs,
                  const ParameterValidator::Result & result);

    void testDescriptions();
    void testValidator();
    void testParameters();

public:
    int Main() override;
};

bool
ParameterTest::assertParameter(const Parameter & exp, const Parameter & act)
{
    bool retval = true;
    if (!EXPECT_EQUAL(exp.getType(), act.getType())) retval = false;
    if (!EXPECT_EQUAL(exp.getValue(), act.getValue())) retval = false;
    if (!EXPECT_EQUAL(exp.asDouble(), act.asDouble())) retval = false;
    if (!EXPECT_EQUAL(exp.asInteger(), act.asInteger())) retval = false;
    if (!EXPECT_EQUAL(exp.asField(), act.asField())) retval = false;
    return retval;
}

bool
ParameterTest::validate(const IIndexEnvironment & env,
                        const std::vector<vespalib::string> & params,
                        const ParameterDescriptions & descs)
{
    ParameterValidator pv(env, params, descs);
    ParameterValidator::Result result = pv.validate();
    LOG(info, "validate(%s)", result.getError().c_str());
    return result.valid();
}

bool
ParameterTest::validate(const IIndexEnvironment & env,
                        const std::vector<vespalib::string> & params,
                        const ParameterDescriptions & descs,
                        const ParameterValidator::Result & result)
{
    if (!validate(env, params, descs)) return false;
    ParameterValidator pv(env, params, descs);
    ParameterValidator::Result actual = pv.validate();
    if (!EXPECT_EQUAL(result.getTag(), actual.getTag())) return false;
    if (!EXPECT_EQUAL(result.getParameters().size(), actual.getParameters().size())) return false;
    bool retval = true;
    for (size_t i = 0; i < result.getParameters().size(); ++i) {
        if (!assertParameter(result.getParameters()[i], actual.getParameters()[i])) retval = false;
    }
    return retval;
}

void
ParameterTest::testDescriptions()
{
    PDS descs = PDS().
        desc().indexField(ParameterCollection::SINGLE).indexField(ParameterCollection::ARRAY).indexField(ParameterCollection::WEIGHTEDSET).attribute(ParameterCollection::ANY).attributeField(ParameterCollection::ANY).field().
        desc(5).feature().number().string().attribute(ParameterCollection::ANY).
        desc().string().number().repeat(2);
    const PDS::DescriptionVector & v = descs.getDescriptions();
    EXPECT_EQUAL(v.size(), 3u);
    EXPECT_EQUAL(v[0].getTag(), 0u);
    EXPECT_TRUE(!v[0].hasRepeat());
    EXPECT_EQUAL(v[0].getParams().size(), 6u);
    EXPECT_EQUAL(v[0].getParam(0).type, ParameterType::INDEX_FIELD);
    EXPECT_EQUAL(v[0].getParam(1).type, ParameterType::INDEX_FIELD);
    EXPECT_EQUAL(v[0].getParam(2).type, ParameterType::INDEX_FIELD);
    EXPECT_EQUAL(v[0].getParam(3).type, ParameterType::ATTRIBUTE);
    EXPECT_EQUAL(v[0].getParam(4).type, ParameterType::ATTRIBUTE_FIELD);
    EXPECT_EQUAL(v[0].getParam(5).type, ParameterType::FIELD);
    EXPECT_EQUAL(v[0].getParam(0).collection, ParameterCollection::SINGLE);
    EXPECT_EQUAL(v[0].getParam(1).collection, ParameterCollection::ARRAY);
    EXPECT_EQUAL(v[0].getParam(2).collection, ParameterCollection::WEIGHTEDSET);
    EXPECT_EQUAL(v[0].getParam(3).collection, ParameterCollection::ANY);
    EXPECT_EQUAL(v[0].getParam(4).collection, ParameterCollection::ANY);
    EXPECT_EQUAL(v[0].getParam(5).collection, ParameterCollection::ANY);

    EXPECT_EQUAL(v[1].getTag(), 5u);
    EXPECT_TRUE(!v[1].hasRepeat());
    EXPECT_EQUAL(v[1].getParams().size(), 4u);
    EXPECT_EQUAL(v[1].getParam(0).type, ParameterType::FEATURE);
    EXPECT_EQUAL(v[1].getParam(1).type, ParameterType::NUMBER);
    EXPECT_EQUAL(v[1].getParam(2).type, ParameterType::STRING);
    EXPECT_EQUAL(v[1].getParam(3).type, ParameterType::ATTRIBUTE);

    EXPECT_EQUAL(v[2].getTag(), 6u);
    EXPECT_TRUE(v[2].hasRepeat());
    EXPECT_EQUAL(v[2].getParams().size(), 2u);
    EXPECT_EQUAL(v[2].getParam(0).type, ParameterType::STRING);
    EXPECT_EQUAL(v[2].getParam(1).type, ParameterType::NUMBER);
    EXPECT_EQUAL(v[2].getParam(2).type, ParameterType::STRING);
    EXPECT_EQUAL(v[2].getParam(3).type, ParameterType::NUMBER);
    EXPECT_EQUAL(v[2].getParam(4).type, ParameterType::STRING);
    EXPECT_EQUAL(v[2].getParam(5).type, ParameterType::NUMBER);
}

void
ParameterTest::testValidator()
{
    IndexEnvironment env;
    IndexEnvironmentBuilder builder(env);
    builder.addField(FieldType::INDEX, CollectionType::SINGLE, "foo")
        .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar")
        .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, DataType::TENSOR, "tbar")
        .addField(FieldType::INDEX, CollectionType::ARRAY, "afoo")
        .addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wfoo")
        .addField(FieldType::INDEX, CollectionType::SINGLE, "hybrid");
    env.getFields().back().addAttribute(); // 'hybrid' field can also be accessed as an attribute

    // valid
    EXPECT_TRUE(validate(env, SL(), PDS().desc()));
    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().field()));
    EXPECT_TRUE(validate(env, SL().add("bar"), PDS().desc().field()));
    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::SINGLE)));
    EXPECT_TRUE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::ARRAY)));
    EXPECT_TRUE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::WEIGHTEDSET)));
    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("bar"), PDS().desc().attribute(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("bar"), PDS().desc().attributeField(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("hybrid"), PDS().desc().attribute(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("baz"), PDS().desc().feature()));
    EXPECT_TRUE(validate(env, SL().add("123"), PDS().desc().number()));
    EXPECT_TRUE(validate(env, SL().add("baz"), PDS().desc().string()));
    EXPECT_TRUE(validate(env, SL().add("tbar"), PDS().desc().attributeField(ParameterCollection::ANY)));
    EXPECT_TRUE(validate(env, SL().add("tbar"), PDS().desc().attribute(ParameterCollection::ANY)));
    // first fail but second pass
    EXPECT_TRUE(validate(env, SL().add("baz"), PDS().desc().field().desc().string()));

    // not valid
    EXPECT_FALSE(validate(env, SL().add("baz"), PDS().desc().string().string()));
    EXPECT_FALSE(validate(env, SL().add("baz").add("baz"), PDS().desc().string()));
    EXPECT_FALSE(validate(env, SL().add("baz"), PDS().desc().field()));
    EXPECT_FALSE(validate(env, SL().add("bar"), PDS().desc().indexField(ParameterCollection::SINGLE)));
    EXPECT_FALSE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::NONE)));
    EXPECT_FALSE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::ARRAY)));
    EXPECT_FALSE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::WEIGHTEDSET)));
    EXPECT_FALSE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::NONE)));
    EXPECT_FALSE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::SINGLE)));
    EXPECT_FALSE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::WEIGHTEDSET)));
    EXPECT_FALSE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::NONE)));
    EXPECT_FALSE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::SINGLE)));
    EXPECT_FALSE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::ARRAY)));
    EXPECT_FALSE(validate(env, SL().add("unknown"), PDS().desc().attribute(ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("unknown"), PDS().desc().attributeField(ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("foo"), PDS().desc().attribute(ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("foo"), PDS().desc().attributeField(ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("hybrid"), PDS().desc().attributeField(ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("12a"), PDS().desc().number()));
    EXPECT_FALSE(validate(env, SL().add("a12"), PDS().desc().number()));
    EXPECT_FALSE(validate(env, SL().add("tbar"), PDS().desc().attributeField(ParameterDataTypeSet::normalTypeSet(), ParameterCollection::ANY)));
    EXPECT_FALSE(validate(env, SL().add("tbar"), PDS().desc().attribute(ParameterDataTypeSet::normalTypeSet(), ParameterCollection::ANY)));

    // test repeat
    PDS d1 = PDS().desc().field().repeat();
    EXPECT_TRUE(validate(env, SL(), d1));
    EXPECT_TRUE(validate(env, SL().add("foo"), d1));
    EXPECT_TRUE(validate(env, SL().add("foo").add("bar"), d1));
    EXPECT_TRUE(!validate(env, SL().add("foo").add("bar").add("baz"), d1));
    PDS d2 = PDS().desc().string().attribute(ParameterCollection::ANY).indexField(ParameterCollection::SINGLE).repeat(2);
    EXPECT_TRUE(validate(env, SL().add("str"), d2));
    EXPECT_TRUE(validate(env, SL().add("str").add("bar").add("foo"), d2));
    EXPECT_TRUE(validate(env, SL().add("str").add("bar").add("foo").add("bar").add("foo"), d2));
    EXPECT_TRUE(!validate(env, SL().add("str").add("bar"), d2));
    EXPECT_TRUE(!validate(env, SL().add("str").add("bar").add("foo").add("bar"), d2));
}

void
ParameterTest::testParameters()
{
    IndexEnvironment env;
    IndexEnvironmentBuilder builder(env);
    builder.addField(FieldType::INDEX, CollectionType::SINGLE, "foo")
        .addField(FieldType::ATTRIBUTE, CollectionType::SINGLE, "bar")
        .addField(FieldType::INDEX, CollectionType::ARRAY, "afoo")
        .addField(FieldType::INDEX, CollectionType::WEIGHTEDSET, "wfoo");

    const FieldInfo * foo = env.getFieldByName("foo");
    const FieldInfo * bar = env.getFieldByName("bar");
    const FieldInfo * afoo = env.getFieldByName("afoo");
    const FieldInfo * wfoo = env.getFieldByName("wfoo");

    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().field(),
                        PVR().addParameter(P(PT::FIELD, "foo").setField(foo)))); // field
    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::SINGLE),
                        PVR().addParameter(P(PT::INDEX_FIELD, "foo").setField(foo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("foo"), PDS().desc().indexField(ParameterCollection::ANY),
                        PVR().addParameter(P(PT::INDEX_FIELD, "foo").setField(foo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::ARRAY),
                        PVR().addParameter(P(PT::INDEX_FIELD, "afoo").setField(afoo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("afoo"), PDS().desc().indexField(ParameterCollection::ANY),
                        PVR().addParameter(P(PT::INDEX_FIELD, "afoo").setField(afoo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::WEIGHTEDSET),
                        PVR().addParameter(P(PT::INDEX_FIELD, "wfoo").setField(wfoo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("wfoo"), PDS().desc().indexField(ParameterCollection::ANY),
                        PVR().addParameter(P(PT::INDEX_FIELD, "wfoo").setField(wfoo)))); // index field
    EXPECT_TRUE(validate(env, SL().add("bar"), PDS().desc().attribute(ParameterCollection::ANY),
                        PVR().addParameter(P(PT::ATTRIBUTE, "bar").setField(bar)))); // attribute field
    EXPECT_TRUE(validate(env, SL().add("feature"), PDS().desc().feature(),
                        PVR().addParameter(P(PT::FEATURE, "feature")))); // feature
    EXPECT_TRUE(validate(env, SL().add("string"), PDS().desc().string(),
                        PVR().addParameter(P(PT::STRING, "string")))); // string

    // numbers
    EXPECT_TRUE(validate(env, SL().add("-100"), PDS().desc().number(),
               PVR().addParameter(P(PT::NUMBER, "-100").setDouble(-100).setInteger(-100))));
    EXPECT_TRUE(validate(env, SL().add("100"), PDS().desc().number(),
               PVR().addParameter(P(PT::NUMBER, "100").setDouble(100).setInteger(100))));
    EXPECT_TRUE(validate(env, SL().add("100.16"), PDS().desc().number(),
               PVR().addParameter(P(PT::NUMBER, "100.16").setDouble(100.16).setInteger(100))));

    EXPECT_TRUE(validate(env, SL(), PDS().desc(), PVR())); // no param
    EXPECT_TRUE(validate(env, SL().add("foo").add("bar"), PDS().desc().string().string(),
                        PVR().addParameter(P(PT::STRING, "foo")).addParameter(P(PT::STRING, "bar")))); // multiple params
    EXPECT_TRUE(validate(env, SL().add("foo").add("bar"), PDS().desc().string().repeat(),
                        PVR().addParameter(P(PT::STRING, "foo")).addParameter(P(PT::STRING, "bar")))); // repeat
    EXPECT_TRUE(validate(env, SL().add("baz"), PDS().desc(10).field().desc(20).string(),
                        PVR(20).addParameter(P(PT::STRING, "baz")))); // second desc matching
}

int
ParameterTest::Main()
{
    TEST_INIT("parameter_test");

    testDescriptions();
    testValidator();
    testParameters();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::fef::ParameterTest);

