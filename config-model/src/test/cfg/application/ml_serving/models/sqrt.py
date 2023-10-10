# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT = helper.make_tensor_value_info('input', TensorProto.FLOAT, [1])
OUTPUT = helper.make_tensor_value_info('out/layer/1:1', TensorProto.FLOAT, [1])

nodes = [
    helper.make_node(
        'Sqrt',
        ['input'],
        ['out/layer/1:1'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'sqrt',
    [INPUT],
    [OUTPUT],
)
model_def = helper.make_model(graph_def, producer_name='sqrt.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'sqrt.onnx')
