// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for datatype.

#include <vespa/document/base/field.h>
#include <vespa/document/datatype/arraydatatype.h>
#include <vespa/document/datatype/structdatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

using namespace document;

namespace {

template <typename S>
void assign(S &lhs, const S &rhs) {
    lhs = rhs;
}

TEST("require that assignment operator works for LongFieldValue") {
    LongFieldValue val;
    val = "1";
    EXPECT_EQUAL(1, val.getValue());
    val = 2;
    EXPECT_EQUAL(2, val.getValue());
    val = static_cast<int64_t>(3);
    EXPECT_EQUAL(3, val.getValue());
    val = 4.0f;
    EXPECT_EQUAL(4, val.getValue());
    val = 5.0;
    EXPECT_EQUAL(5, val.getValue());
}

TEST("require that StructDataType can redeclare identical fields.") {
    StructDataType s("foo");
    Field field1("field1", 42, *DataType::STRING);
    Field field2("field2", 42, *DataType::STRING);

    s.addField(field1);
    s.addField(field1);  // ok
    s.addInheritedField(field1);  // ok
    EXPECT_EXCEPTION(s.addField(field2), vespalib::IllegalArgumentException,
                     "Field id in use by field Field(field1");
    s.addInheritedField(field2);
    EXPECT_FALSE(s.hasField(field2.getName()));
}

class TensorDataTypeFixture {
    std::unique_ptr<const TensorDataType> _tensorDataType;
public:
    using ValueType = vespalib::eval::ValueType;
    TensorDataTypeFixture()
        : _tensorDataType()
    {
    }

    ~TensorDataTypeFixture();

    void setup(const vespalib::string &spec)
    {
        _tensorDataType = TensorDataType::fromSpec(spec);
    }

    bool isAssignableType(const vespalib::string &spec) const
    {
        auto assignType = ValueType::from_spec(spec);
        return _tensorDataType->isAssignableType(assignType);
    }
};

TensorDataTypeFixture::~TensorDataTypeFixture() = default;

TEST_F("require that TensorDataType can check for assignable tensor type", TensorDataTypeFixture)
{
    f.setup("tensor(x[2])");
    EXPECT_TRUE(f.isAssignableType("tensor(x[2])"));
    EXPECT_FALSE(f.isAssignableType("tensor(x[3])"));
    EXPECT_FALSE(f.isAssignableType("tensor(y[2])"));
    EXPECT_FALSE(f.isAssignableType("tensor(x{})"));
}

TEST("TensorDataType implements equals() that takes underlying tensor type into consideration")
{
    auto a = TensorDataType::fromSpec("tensor<float>(x[4])");
    auto b = TensorDataType::fromSpec("tensor<bfloat16>(x[4])");
    EXPECT_EQUAL(*a, *a);
    EXPECT_NOT_EQUAL(*a, *b);
    EXPECT_NOT_EQUAL(*b, *a);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
