# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
import numpy as np
from onnx import helper, TensorProto

# Define inputs and output
INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [1])
INPUT_2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, [1])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.FLOAT, [1])

# Create a constant tensor that will be stored externally
# This constant will be added to the sum of input1 and input2
constant_data = np.array([5.0], dtype=np.float32)
constant_tensor = helper.make_tensor(
    name='constant_value',
    data_type=TensorProto.FLOAT,
    dims=[1],
    vals=constant_data.tobytes(),
    raw=True
)

# Set external data location for the constant tensor
external_data_entry = onnx.StringStringEntryProto()
external_data_entry.key = "location"
external_data_entry.value = "external_data.bin"
constant_tensor.external_data.append(external_data_entry)

offset_entry = onnx.StringStringEntryProto()
offset_entry.key = "offset"
offset_entry.value = "0"
constant_tensor.external_data.append(offset_entry)

length_entry = onnx.StringStringEntryProto()
length_entry.key = "length"
length_entry.value = str(len(constant_data.tobytes()))
constant_tensor.external_data.append(length_entry)

constant_tensor.data_location = onnx.TensorProto.EXTERNAL

# Create nodes for the computation graph
nodes = [
    # First add: input1 + input2 = intermediate_sum
    helper.make_node(
        'Add',
        ['input1', 'input2'],
        ['intermediate_sum'],
        name='add_inputs'
    ),
    # Second add: intermediate_sum + constant_value = output
    helper.make_node(
        'Add',
        ['intermediate_sum', 'constant_value'],
        ['output'],
        name='add_constant'
    ),
]

# Create the graph
graph_def = helper.make_graph(
    nodes,
    'add_with_external_data',
    [INPUT_1, INPUT_2],  # inputs
    [OUTPUT],            # outputs
    [constant_tensor]    # initializers (constants)
)

# Create the model with IR version 10
model_def = helper.make_model(
    graph_def, 
    producer_name='add_with_external_data.py', 
    opset_imports=[onnx.OperatorSetIdProto(version=12)]
)

# Set the IR version to 10 explicitly
model_def.ir_version = 10

# Write the external data file
with open('external_data.bin', 'wb') as f:
    f.write(constant_data.tobytes())

# Save the ONNX model
onnx.save(model_def, 'add_with_external_data.onnx')

print("Model saved as 'add_with_external_data.onnx'")
print("External data saved as 'external_data.bin'")
print(f"Constant value stored externally: {constant_data[0]}")
