// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tensor_update.h"
#include "valueupdate.h"

namespace vespalib::eval { struct Value; struct ValueBuilderFactory; }

namespace document {

class TensorFieldValue;

/**
 * An update used to add cells to a sparse or mixed tensor.
 *
 * The cells to add are contained in a tensor of the same type.
 */
class TensorAddUpdate final : public ValueUpdate, public TensorUpdate {
    std::unique_ptr<TensorFieldValue> _tensor;

    friend ValueUpdate;
    TensorAddUpdate();
    ACCEPT_UPDATE_VISITOR;
public:
    explicit TensorAddUpdate(std::unique_ptr<TensorFieldValue> tensor);
    TensorAddUpdate(const TensorAddUpdate &rhs) = delete;
    TensorAddUpdate &operator=(const TensorAddUpdate &rhs) = delete;
    ~TensorAddUpdate() override;
    bool operator==(const ValueUpdate &other) const override;
    const TensorFieldValue &getTensor() const { return *_tensor; }
    void checkCompatibility(const Field &field) const override;
    std::unique_ptr<vespalib::eval::Value> applyTo(const vespalib::eval::Value &tensor) const;
    std::unique_ptr<Value> apply_to(const Value &tensor,
                                    const ValueBuilderFactory &factory) const override;
    bool applyTo(FieldValue &value) const override;
    void printXml(XmlOutputStream &xos) const override;
    void print(std::ostream &out, bool verbose, const std::string &indent) const override;
    void deserialize(const DocumentTypeRepo &repo, const DataType &type, nbostream &stream) override;
};

}
