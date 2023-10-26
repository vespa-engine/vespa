# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

IN1 = helper.make_tensor_value_info('in1', TensorProto.FLOAT, ['batch1'])
IN2 = helper.make_tensor_value_info('in2', TensorProto.FLOAT, ['batch2'])
OUT = helper.make_tensor_value_info('out', TensorProto.FLOAT, ['batch3'])

nodes = [
    helper.make_node(
        'Add',
        ['in1', 'in2'],
        ['out'],
    ),
]
graph_def = helper.make_graph(
    nodes,
    'guess_batch',
    [
        IN1,
        IN2,
    ],
    [OUT],
)
model_def = helper.make_model(graph_def, producer_name='guess_batch.py', opset_imports=[onnx.OperatorSetIdProto(version=12)])
onnx.save(model_def, 'guess_batch.onnx')
