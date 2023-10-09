# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import onnx
from onnx import helper, TensorProto

INPUT1 = helper.make_tensor_value_info('in1', TensorProto.FLOAT, [2])
INPUT2 = helper.make_tensor_value_info('in2', TensorProto.FLOAT, ['batch'])

OUTPUT = helper.make_tensor_value_info('out', TensorProto.FLOAT, [2])

nodes = [
    helper.make_node(
        'Add',
        ['in1', 'in2'],
        ['out'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'fragile',
    [
        INPUT1,
        INPUT2,
    ],
    [
        OUTPUT,
    ],
)
model_def = helper.make_model(graph_def, producer_name='fragile.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'fragile.onnx')
