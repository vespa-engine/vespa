# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [1])
INPUT_2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, [1])
OUTPUT_1 = helper.make_tensor_value_info('output1', TensorProto.FLOAT, [1])
OUTPUT_2 = helper.make_tensor_value_info('output2', TensorProto.FLOAT, [1])

nodes = [
    helper.make_node(
        'Mul',
        ['input1', 'input2'],
        ['output1'],
    ),
    helper.make_node(
        'Add',
        ['input1', 'input2'],
        ['output2'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'add_mul',
    [INPUT_1, INPUT_2],
    [OUTPUT_1, OUTPUT_2],
)
model_def = helper.make_model(graph_def, producer_name='add_mul.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'add_mul.onnx')
