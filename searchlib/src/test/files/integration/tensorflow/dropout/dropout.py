
# Common imports
import numpy as np
import tensorflow as tf
import datetime

now = datetime.datetime.utcnow().strftime("%Y%m%d%H%M%S")
root_logdir = "tf_logs"
logdir = "{}/run-{}/".format(root_logdir, now)

n_inputs = 784
n_outputs = 10
dropout_rate = 0.5  # == 1 - keep_prob

X = tf.placeholder(tf.float32, shape=(None, n_inputs), name="X")
y = tf.placeholder(tf.int64, shape=(None), name="y")
training = tf.placeholder_with_default(False, shape=(), name='training')

def leaky_relu_with_small_constant(z, name=None):
    return tf.maximum(tf.constant(0.01, shape=[1]) * z, z, name=name)

X_drop = tf.layers.dropout(X, dropout_rate, training=training, name="xdrop")
output = tf.layers.dense(X_drop, n_outputs, activation=leaky_relu_with_small_constant, name="outputs")

init = tf.global_variables_initializer()
file_writer = tf.summary.FileWriter(logdir, tf.get_default_graph())

with tf.Session() as sess:
    init.run()
    sess.run(output, feed_dict={training: False, X: np.random.random((1,784))})

    export_path = "saved"
    print('Exporting trained model to ', export_path)
    builder = tf.saved_model.builder.SavedModelBuilder(export_path)
    signature = tf.saved_model.signature_def_utils.predict_signature_def(inputs = {'x':X}, outputs = {'y':output})
    builder.add_meta_graph_and_variables(sess,
                                         [tf.saved_model.tag_constants.SERVING],
                                         signature_def_map={'serving_default':signature})
    builder.save(as_text=True)

file_writer.close()


