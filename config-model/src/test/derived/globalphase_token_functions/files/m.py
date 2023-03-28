# imports

from onnx import TensorProto
from onnx.helper import (
    make_model, make_node, make_graph,
    make_tensor_value_info, make_value_info)
from onnx.checker import check_model

# inputs

# TensorProto.DOUBLE is the element type, [128] the shape
A = make_tensor_value_info('input_ids',      TensorProto.DOUBLE, [128])
B = make_tensor_value_info('attention_mask', TensorProto.DOUBLE, [128])
C = make_tensor_value_info('token_type_ids', TensorProto.DOUBLE, [128])

# outputs, the shape is defined
Y = make_tensor_value_info('vector_Y', TensorProto.DOUBLE, [128])
S = make_tensor_value_info('score', TensorProto.DOUBLE, [1])

# Creates node defined by the operator type, inputs, outputs, and possibly options
node1 = make_node('Mul', ['input_ids', 'attention_mask'], ['masked'])
node2 = make_node('Add', ['masked', 'token_type_ids'], ['vector_Y'])
node3 = make_node('ReduceSum', inputs=['vector_Y'], outputs=['score'], keepdims=1)

# from nodes to graph
# the graph is built from the list of nodes, the list of inputs,
# the list of outputs and a name.

graph = make_graph([node1, node2, node3],  # nodes
                    'ranking_model',  # a name
                    [A, B, C],  # inputs
                    [S])  # outputs

# onnx graph to model
onnx_model = make_model(graph)

# ensure we do not get too new opset version:
del onnx_model.opset_import[:]
opset = onnx_model.opset_import.add()
opset.version = 17

# Let's check the model is consistent, this function is described in
# section Checker and Shape Inference.
check_model(onnx_model)

# The serialization
with open("ranking_model.onnx", "wb") as f:
    f.write(onnx_model.SerializeToString())

# the work is done, let's display it...
print(onnx_model)
