# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT1 = helper.make_tensor_value_info('input1', TensorProto.FLOAT, [2, 3])
INPUT2 = helper.make_tensor_value_info('input2', TensorProto.FLOAT, [3, 4])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.FLOAT, [2, 4])

nodes = [
    helper.make_node(
        'MatMul',
        ['input1', 'input2'],
        ['output'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'matmul',
    [
        INPUT1,
        INPUT2,
    ],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='matmul.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'matmul.onnx')
