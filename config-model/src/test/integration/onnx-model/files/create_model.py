# Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('first_input', TensorProto.FLOAT, [2])
INPUT_2 = helper.make_tensor_value_info('second/input:0', TensorProto.FLOAT, [2])
INPUT_3 = helper.make_tensor_value_info('third_input', TensorProto.FLOAT, [2])
OUTPUT_1 = helper.make_tensor_value_info('path/to/output:0', TensorProto.FLOAT, [2])
OUTPUT_2 = helper.make_tensor_value_info('path/to/output:1', TensorProto.FLOAT, [2])
OUTPUT_3 = helper.make_tensor_value_info('path/to/output:2', TensorProto.FLOAT, [2])

nodes = [
    helper.make_node(
        'Add',
        ['first_input', 'second/input:0'],
        ['path/to/output:0'],
    ),
    helper.make_node(
        'Add',
        ['third_input', 'second/input:0'],
        ['path/to/output:1']
    ),
    helper.make_node(
        'Add',
        ['path/to/output:0', 'path/to/output:1'],
        ['path/to/output:2']
    ),
]
graph_def = helper.make_graph(
    nodes,
    'simple_scoring',
    [INPUT_1, INPUT_2, INPUT_3],
    [OUTPUT_1, OUTPUT_2, OUTPUT_3]
)
model_def = helper.make_model(graph_def, producer_name='create_model.py')
onnx.save(model_def, 'model.onnx')
