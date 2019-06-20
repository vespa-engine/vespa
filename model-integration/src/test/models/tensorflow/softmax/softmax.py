# Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import numpy as np
import tensorflow as tf

# Creates simple random neural network that has softmax on output. No training.

n_inputs = 5
n_outputs = 3

input = tf.placeholder(tf.float32, shape=(None, n_inputs), name="input")
W = tf.Variable(tf.random.uniform([n_inputs, n_outputs]), name="weights")
b = tf.Variable(tf.random.uniform([n_outputs]), name="bias")
Z = tf.matmul(input, W) + b
hidden_layer = tf.nn.relu(Z)
output_layer = tf.nn.softmax(hidden_layer, name="output")

init = tf.global_variables_initializer()

with tf.Session() as sess:
    init.run()
    export_path = "saved"
    builder = tf.saved_model.builder.SavedModelBuilder(export_path)
    signature = tf.saved_model.signature_def_utils.predict_signature_def(inputs = {'x':input}, outputs = {'y':output_layer})
    builder.add_meta_graph_and_variables(sess,
                                         [tf.saved_model.tag_constants.SERVING],
                                         signature_def_map={'serving_default':signature})
    builder.save(as_text=True)

