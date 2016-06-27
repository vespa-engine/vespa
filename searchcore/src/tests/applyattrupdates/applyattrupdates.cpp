// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/clearvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/log/log.h>
#include <vespa/searchcore/proton/common/attrupdate.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/vespalib/testkit/testapp.h>
#include <cmath>

LOG_SETUP("applyattrupdates_test");

using namespace document;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::CollectionType;

namespace search {

//-----------------------------------------------------------------------------

template <typename T>
class Vector
{
private:
    std::vector<T> _vec;
public:
    Vector() : _vec() {}
    size_t size() const {
        return _vec.size();
    }
    Vector & pb(const T & val) {
        _vec.push_back(val);
        return *this;
    }
    const T & operator [] (size_t idx) const {
        return _vec[idx];
    }
};

//-----------------------------------------------------------------------------

typedef AttributeVector::SP AttributePtr;
typedef AttributeVector::WeightedInt WeightedInt;
typedef AttributeVector::WeightedFloat WeightedFloat;
typedef AttributeVector::WeightedString WeightedString;

class Test : public vespalib::TestApp
{
private:
    template <typename T, typename VectorType>
    AttributePtr
    create(uint32_t numDocs, T val, int32_t weight,
           const std::string & baseName,
           const Config &info)
    {
        LOG(info, "create attribute vector: %s", baseName.c_str());
        AttributePtr vec = AttributeFactory::createAttribute(baseName, info);
        VectorType * api = static_cast<VectorType *>(vec.get());
        for (uint32_t i = 0; i < numDocs; ++i) {
            if (!api->addDoc(i)) {
                LOG(info, "failed adding doc: %u", i);
                return AttributePtr();
            }
            if (api->hasMultiValue()) {
                if (!api->append(i, val, weight)) {
                    LOG(info, "failed append to doc: %u", i);
                }
            } else {
                if (!api->update(i, val)) {
                    LOG(info, "failed update doc: %u", i);
                    return AttributePtr();
                }
            }
        }
        api->commit();
        return vec;
    }

    template <typename T>
    bool check(const AttributePtr & vec, uint32_t docId, const Vector<T> & values) {
        uint32_t sz = vec->getValueCount(docId);
        if (!EXPECT_EQUAL(sz, values.size())) return false;
        std::vector<T> buf(sz);
        uint32_t asz = vec->get(docId, &buf[0], sz);
        if (!EXPECT_EQUAL(sz, asz)) return false;
        for (uint32_t i = 0; i < values.size(); ++i) {
            if (!EXPECT_EQUAL(buf[i].getValue(), values[i].getValue())) return false;
            if (!EXPECT_EQUAL(buf[i].getWeight(), values[i].getWeight())) return false;
        }
        return true;
    }

    void applyValueUpdate(AttributeVector & vec, uint32_t docId, const ValueUpdate & upd) {
        FieldUpdate fupd(_docType->getField(vec.getName()));
        fupd.addUpdate(upd);
        search::AttrUpdate::handleUpdate(vec, docId, fupd);
        vec.commit();
    }

    void applyArrayUpdates(AttributeVector & vec, const FieldValue & assign,
                           const FieldValue & first, const FieldValue & second) {
        applyValueUpdate(vec, 0, AssignValueUpdate(assign));
        applyValueUpdate(vec, 1, AddValueUpdate(second));
        applyValueUpdate(vec, 2, RemoveValueUpdate(first));
        applyValueUpdate(vec, 3, ClearValueUpdate());
    }

    void applyWeightedSetUpdates(AttributeVector & vec, const FieldValue & assign,
                                 const FieldValue & first, const FieldValue & second) {
        applyValueUpdate(vec, 0, AssignValueUpdate(assign));
        applyValueUpdate(vec, 1, AddValueUpdate(second, 20));
        applyValueUpdate(vec, 2, RemoveValueUpdate(first));
        applyValueUpdate(vec, 3, ClearValueUpdate());
        ArithmeticValueUpdate arithmetic(ArithmeticValueUpdate::Add, 10);
        applyValueUpdate(vec, 4, MapValueUpdate(first, arithmetic));
    }

    void requireThatSingleAttributesAreUpdated();
    void requireThatArrayAttributesAreUpdated();
    void requireThatWeightedSetAttributesAreUpdated();

    DocumentTypeRepo _repo;
    const DocumentType* _docType;

public:
    Test();
    int Main();
};

void
Test::requireThatSingleAttributesAreUpdated()
{
    using search::attribute::getUndefined;
    CollectionType ct(CollectionType::SINGLE);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(3, 32, 0,
                                                             "in1/int",
                                                             Config(bt, ct));
        applyValueUpdate(*vec, 0, AssignValueUpdate(IntFieldValue(64)));
        applyValueUpdate(*vec, 1, ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedInt>().pb(WeightedInt(64))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedInt>().pb(WeightedInt(42))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedInt>().pb(WeightedInt(getUndefined<int32_t>()))));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(3, 55.5f, 0,
                                                                 "in1/float",
                                                                 Config(bt,
                                                                        ct));
        applyValueUpdate(*vec, 0, AssignValueUpdate(FloatFieldValue(77.7f)));
        applyValueUpdate(*vec, 1, ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10));
        applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedFloat>().pb(WeightedFloat(77.7f))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedFloat>().pb(WeightedFloat(65.5f))));
        EXPECT_TRUE(std::isnan(vec->getFloat(2)));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(3, "first", 0,
                                                                "in1/string",
                                                                Config(bt,
                                                                       ct));
        applyValueUpdate(*vec, 0, AssignValueUpdate(StringFieldValue("second")));
        applyValueUpdate(*vec, 2, ClearValueUpdate());
        EXPECT_EQUAL(3u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedString>().pb(WeightedString("second"))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedString>().pb(WeightedString("first"))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedString>().pb(WeightedString(""))));
    }
}

void
Test::requireThatArrayAttributesAreUpdated()
{
    CollectionType ct(CollectionType::ARRAY);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(5, 32, 1,
                                                             "in1/aint",
                                                             Config(bt, ct));
        IntFieldValue first(32);
        IntFieldValue second(64);
        ArrayFieldValue assign(_docType->getField("aint").getDataType());
        assign.add(second);
        applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedInt>().pb(WeightedInt(64))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedInt>().pb(WeightedInt(32)).pb(WeightedInt(64))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedInt>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedInt>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedInt>().pb(WeightedInt(32))));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(5, 55.5f, 1,
                                                                 "in1/afloat",
                                                                 Config(bt,
                                                                        ct));
        FloatFieldValue first(55.5f);
        FloatFieldValue second(77.7f);
        ArrayFieldValue assign(_docType->getField("afloat").getDataType());
        assign.add(second);
        applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedFloat>().pb(WeightedFloat(77.7f))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedFloat>().pb(WeightedFloat(55.5f)).pb(WeightedFloat(77.7f))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedFloat>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedFloat>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedFloat>().pb(WeightedFloat(55.5f))));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(5, "first", 1,
                                                                "in1/astring",
                                                                Config(bt, ct));
        StringFieldValue first("first");
        StringFieldValue second("second");
        ArrayFieldValue assign(_docType->getField("astring").getDataType());
        assign.add(second);
        applyArrayUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedString>().pb(WeightedString("second"))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedString>().pb(WeightedString("first")).pb(WeightedString("second"))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedString>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedString>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedString>().pb(WeightedString("first"))));
    }
}

void
Test::requireThatWeightedSetAttributesAreUpdated()
{
    CollectionType ct(CollectionType::WSET);
    {
        BasicType bt(BasicType::INT32);
        AttributePtr vec = create<int32_t, IntegerAttribute>(5, 32, 100,
                                                             "in1/wsint",
                                                             Config(bt, ct));
        IntFieldValue first(32);
        IntFieldValue second(64);
        WeightedSetFieldValue
            assign(_docType->getField("wsint").getDataType());
        assign.add(second, 20);
        applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedInt>().pb(WeightedInt(64, 20))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedInt>().pb(WeightedInt(32, 100)).pb(WeightedInt(64, 20))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedInt>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedInt>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedInt>().pb(WeightedInt(32, 110))));
    }
    {
        BasicType bt(BasicType::FLOAT);
        AttributePtr vec = create<float, FloatingPointAttribute>(5, 55.5f, 100,
                                                                 "in1/wsfloat",
                                                                 Config(bt,
                                                                        ct));
        FloatFieldValue first(55.5f);
        FloatFieldValue second(77.7f);
        WeightedSetFieldValue
            assign(_docType->getField("wsfloat").getDataType());
        assign.add(second, 20);
        applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedFloat>().pb(WeightedFloat(77.7f, 20))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedFloat>().pb(WeightedFloat(55.5f, 100)).pb(WeightedFloat(77.7f, 20))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedFloat>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedFloat>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedFloat>().pb(WeightedFloat(55.5f, 110))));
    }
    {
        BasicType bt(BasicType::STRING);
        AttributePtr vec = create<std::string, StringAttribute>(5, "first",
                                                                100,
                                                                "in1/wsstring",
                                                                Config(bt,
                                                                       ct));
        StringFieldValue first("first");
        StringFieldValue second("second");
        WeightedSetFieldValue
            assign(_docType->getField("wsstring").getDataType());
        assign.add(second, 20);
        applyWeightedSetUpdates(*vec, assign, first, second);

        EXPECT_EQUAL(5u, vec->getNumDocs());
        EXPECT_TRUE(check(vec, 0, Vector<WeightedString>().pb(WeightedString("second", 20))));
        EXPECT_TRUE(check(vec, 1, Vector<WeightedString>().pb(WeightedString("first", 100)).pb(WeightedString("second", 20))));
        EXPECT_TRUE(check(vec, 2, Vector<WeightedString>()));
        EXPECT_TRUE(check(vec, 3, Vector<WeightedString>()));
        EXPECT_TRUE(check(vec, 4, Vector<WeightedString>().pb(WeightedString("first", 110))));
    }
}

Test::Test()
    : _repo(readDocumenttypesConfig("doctypes.cfg")),
      _docType(_repo.getDocumentType("testdoc"))
{
}

int
Test::Main()
{
    TEST_INIT("applyattrupdates_test");

    TEST_DO(requireThatSingleAttributesAreUpdated());
    TEST_DO(requireThatArrayAttributesAreUpdated());
    TEST_DO(requireThatWeightedSetAttributesAreUpdated());

    TEST_DONE();
}

} // namespace search

TEST_APPHOOK(search::Test);
