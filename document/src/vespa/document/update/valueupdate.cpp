// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valueupdate.h"
#include "addvalueupdate.h"
#include "assignvalueupdate.h"
#include "arithmeticvalueupdate.h"
#include "clearvalueupdate.h"
#include "removevalueupdate.h"
#include "mapvalueupdate.h"
#include "tensor_add_update.h"
#include "tensor_modify_update.h"
#include "tensor_remove_update.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

namespace document {

const char *
ValueUpdate::className() const noexcept {
    switch (getType()) {
        case Add:
            return "AddValueUpdate";
        case Assign:
            return "AssignValueUpdate";
        case Arithmetic:
            return "ArithmeticValueUpdate";
        case Clear:
            return "ClearValueUpdate";
        case Remove:
            return "RemoveValueUpdate";
        case Map:
            return "MapValueUpdate";
        case TensorAdd:
            return "TensorAddUpdate";
        case TensorModify:
            return "TensorModifyUpdate";
        case TensorRemove:
            return "TensorRemoveUpdate";
        default:
            abort();
    }
}

std::unique_ptr<ValueUpdate>
ValueUpdate::create(ValueUpdateType type) {
    switch (type) {
        case Add:
            return std::unique_ptr<AddValueUpdate>(new AddValueUpdate());
        case Assign:
            return std::make_unique<AssignValueUpdate>();
        case Arithmetic:
            return std::unique_ptr<ArithmeticValueUpdate>(new ArithmeticValueUpdate());
        case Clear:
            return std::make_unique<ClearValueUpdate>();
        case Remove:
            return std::unique_ptr<RemoveValueUpdate>(new RemoveValueUpdate());
        case Map:
            return std::unique_ptr<MapValueUpdate>( new MapValueUpdate());
        case TensorAdd:
            return std::unique_ptr<TensorAddUpdate>( new TensorAddUpdate());
        case TensorModify:
            return std::unique_ptr<TensorModifyUpdate>( new TensorModifyUpdate());
        case TensorRemove:
            return std::unique_ptr<TensorRemoveUpdate>( new TensorRemoveUpdate());
        default:
            throw std::runtime_error(vespalib::make_string("Could not find a class for classId %d(%x)", type, type));
    }
}

std::unique_ptr<ValueUpdate>
ValueUpdate::createInstance(const DocumentTypeRepo& repo, const DataType& type, nbostream & stream)
{
    int32_t classId = 0;
    stream >> classId;

    std::unique_ptr<ValueUpdate> update = create(static_cast<ValueUpdateType>(classId));

    /// \todo TODO (was warning):  Updates are not versioned in serialization format. Will not work without altering it.
    /// Should also use the serializer, not this deserialize into self.
    update->deserialize(repo, type, stream);
    return update;
}

std::ostream&
operator<<(std::ostream& out, const ValueUpdate& p) {
    p.print(out, false, "");
    return out;
}

}
