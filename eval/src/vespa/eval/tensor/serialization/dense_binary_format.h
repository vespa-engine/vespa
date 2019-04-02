// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
namespace vespalib { class nbostream; }

namespace vespalib::tensor {

class DenseTensor;
class DenseTensorView;

/**
 * Class for serializing a dense tensor.
 */
class DenseBinaryFormat
{
public:
    enum class EncodeType { NO_DEFAULT, DOUBLE_IS_DEFAULT};
    DenseBinaryFormat(EncodeType encodeType) : _encodeType(encodeType) { }
    void serialize(nbostream &stream, const DenseTensorView &tensor);
    std::unique_ptr<DenseTensor> deserialize(nbostream &stream);
private:
    EncodeType _encodeType;
};

}
