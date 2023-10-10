# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.DOUBLE, [1])
INPUT_2 = helper.make_tensor_value_info('input2', TensorProto.DOUBLE, [1])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.DOUBLE, [1])

nodes = [
    helper.make_node(
        'Add',
        ['input1', 'input2'],
        ['output'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'add',
    [
        INPUT_1,
        INPUT_2
    ],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='add_double.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'add_double.onnx')
