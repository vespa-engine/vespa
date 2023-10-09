# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import onnx
from onnx import helper, TensorProto

IN8 = helper.make_tensor_value_info('in8', TensorProto.INT8, [3])
IN16 = helper.make_tensor_value_info('in16', TensorProto.BFLOAT16, [3])
OUT8 = helper.make_tensor_value_info('out8', TensorProto.INT8, [3])
OUT16 = helper.make_tensor_value_info('out16', TensorProto.BFLOAT16, [3])

nodes = [
    helper.make_node(
        'Cast',
        ['in8'],
        ['out16'],
        to=getattr(TensorProto, 'BFLOAT16')
    ),
    helper.make_node(
        'Cast',
        ['in16'],
        ['out8'],
        to=getattr(TensorProto, 'INT8')
    ),
]
graph_def = helper.make_graph(
    nodes,
    'unstable_types',
    [IN8, IN16],
    [OUT8, OUT16],
)
model_def = helper.make_model(graph_def, producer_name='unstable_types.py', opset_imports=[onnx.OperatorSetIdProto(version=13)])
onnx.save(model_def, 'unstable_types.onnx')
