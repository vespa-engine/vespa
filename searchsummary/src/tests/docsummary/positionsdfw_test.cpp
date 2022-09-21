// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/juniper/rpinterface.h>
#include <vespa/searchlib/attribute/extendableattributes.h>
#include <vespa/searchlib/attribute/iattributemanager.h>
#include <vespa/searchlib/common/matching_elements.h>
#include <vespa/searchsummary/docsummary/docsum_field_writer.h>
#include <vespa/searchsummary/docsummary/docsumstate.h>
#include <vespa/searchsummary/docsummary/idocsumenvironment.h>
#include <vespa/searchsummary/docsummary/positionsdfw.h>
#include <vespa/searchsummary/test/slime_value.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("positionsdfw_test");

using search::IAttributeManager;
using search::MatchingElements;
using search::SingleInt64ExtAttribute;
using search::attribute::IAttributeContext;
using search::attribute::IAttributeVector;
using search::attribute::IAttributeFunctor;
using vespalib::string;
using std::vector;

namespace search::docsummary {

namespace {

class MyAttributeContext : public IAttributeContext {
    const IAttributeVector &_attr;
public:
    MyAttributeContext(const IAttributeVector &attr) : _attr(attr) {}
     const IAttributeVector *getAttribute(const string &) const override {
        return &_attr;
    }
    const IAttributeVector *getAttributeStableEnum(const string &) const override {
        LOG_ABORT("MyAttributeContext::getAttributeStableEnum should not be reached");
    }
    void getAttributeList(vector<const IAttributeVector *> &) const override {
        LOG_ABORT("MyAttributeContext::getAttributeList should not be reached");
    }

    void
    asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const override {
        LOG_ABORT("MyAttributeContext::asyncForAttribute should not be reached");
    }
};

class MyAttributeManager : public IAttributeManager {
    const IAttributeVector &_attr;
public:

    MyAttributeManager(const IAttributeVector &attr) : _attr(attr) {}
    AttributeGuard::UP getAttribute(const string &) const override {
        LOG_ABORT("should not be reached");
    }
    std::unique_ptr<attribute::AttributeReadGuard> getAttributeReadGuard(const string &, bool) const override {
        LOG_ABORT("should not be reached");
    }
    void getAttributeList(vector<AttributeGuard> &) const override {
        LOG_ABORT("should not be reached");
    }

    void
    asyncForAttribute(const vespalib::string &, std::unique_ptr<IAttributeFunctor>) const override {
        LOG_ABORT("should not be reached");
    }

    IAttributeContext::UP createContext() const override {
        return std::make_unique<MyAttributeContext>(_attr);
    }

    std::shared_ptr<attribute::ReadableAttributeVector> readable_attribute_vector(const string&) const override {
        LOG_ABORT("should not be reached");
    }
};

struct MyGetDocsumsStateCallback : GetDocsumsStateCallback {
    virtual void fillSummaryFeatures(GetDocsumsState&) override {}
    virtual void fillRankFeatures(GetDocsumsState&) override {}
    std::unique_ptr<MatchingElements> fill_matching_elements(const MatchingElementsFields &) override { abort(); }
};

template <typename AttrType>
void checkWritePositionField(AttrType &attr,
                             uint32_t doc_id, const vespalib::string &expect_json) {
    for (AttributeVector::DocId i = 0; i < doc_id + 1; ) {
        attr.addDoc(i);
        if (i == 007) {
            attr.add((int64_t) -1);
        } else if (i == 0x42) {
            attr.add(0xAAAAaaaaAAAAaaaa);
        } else if (i == 0x17) {
            attr.add(0x5555aaaa5555aaab);
        } else if (i == 42) {
            attr.add(0x8000000000000000);
        } else {
            attr.add(i); // value = docid
        }
    }

    MyAttributeManager attribute_man(attr);
    PositionsDFW::UP writer = PositionsDFW::create(attr.getName().c_str(), &attribute_man, false);
    ASSERT_TRUE(writer.get());
    MyGetDocsumsStateCallback callback;
    GetDocsumsState state(callback);
    state._attributes.push_back(&attr);

    vespalib::Slime target;
    vespalib::slime::SlimeInserter inserter(target);
    writer->insertField(doc_id, state, inserter);

    test::SlimeValue expected(expect_json);
    EXPECT_EQ(expected.slime, target);
}

}  // namespace

TEST(PositionsDFWTest, require_that_2D_position_field_is_written)
{
    SingleInt64ExtAttribute attr("foo");
    checkWritePositionField(attr, 0x3e, "{x:6,y:7,latlong:'N0.000007;E0.000006'}");
    checkWritePositionField(attr,  007, "{x:-1,y:-1,latlong:'S0.000001;W0.000001'}");
    checkWritePositionField(attr, 0x42, "{x:0,y:-1,latlong:'S0.000001;E0.000000'}");
    checkWritePositionField(attr, 0x17, "{x:-16711935,y:16711935,latlong:'N16.711935;W16.711935'}");
    checkWritePositionField(attr,   42, "null");
}

}

GTEST_MAIN_RUN_ALL_TESTS()
