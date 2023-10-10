# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

IN = helper.make_tensor_value_info('in', TensorProto.FLOAT, [7])
OUT = helper.make_tensor_value_info('out', TensorProto.INT8, [7])

nodes = [
    helper.make_node(
        'Cast',
        ['in'],
        ['out'],
        to=getattr(TensorProto, 'INT8'),
    ),
]
graph_def = helper.make_graph(
    nodes,
    'float_to_int8',
    [IN],
    [OUT],
)
model_def = helper.make_model(graph_def, producer_name='float_to_int8.py', opset_imports=[onnx.OperatorSetIdProto(version=13)])
onnx.save(model_def, 'float_to_int8.onnx')
