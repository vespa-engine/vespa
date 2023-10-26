# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT_1 = helper.make_tensor_value_info('input1', TensorProto.BFLOAT16, [1])
OUTPUT = helper.make_tensor_value_info('output', TensorProto.BFLOAT16, [1])

nodes = [
    helper.make_node(
        'Sign',
        ['input1'],
        ['output'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'sign',
    [
        INPUT_1
    ],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='sign_bfloat16.py', opset_imports=[onnx.OperatorSetIdProto(version=19)])
onnx.save(model_def, 'sign_bfloat16.onnx')
