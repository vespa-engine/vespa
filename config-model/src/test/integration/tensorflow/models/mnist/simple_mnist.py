
# Common imports
import numpy as np
import tensorflow as tf

from tensorflow.examples.tutorials.mnist import input_data
from datetime import datetime

now = datetime.utcnow().strftime("%Y%m%d%H%M%S")
root_logdir = "tf_logs"
logdir = "{}/run-{}/".format(root_logdir, now)

mnist = input_data.read_data_sets("/tmp/data/")
X_train = mnist.train.images
X_test = mnist.test.images
y_train = mnist.train.labels.astype("int")
y_test = mnist.test.labels.astype("int")

n_inputs = 28*28  # MNIST
n_hidden1 = 300
n_hidden2 = 100
n_hidden3 = 40
n_outputs = 10

learning_rate = 0.01
n_epochs = 20
batch_size = 50

input = tf.placeholder(tf.float32, shape=(None, n_inputs), name="input")
y = tf.placeholder(tf.int64, shape=(None), name="y")


def neuron_layer(X, n_neurons, name, activation=None):
    with tf.name_scope(name):
        n_inputs = int(X.get_shape()[1])
        stddev = 2 / np.sqrt(n_inputs)
        init = tf.truncated_normal((n_inputs, n_neurons), stddev=stddev)
        W = tf.Variable(init, name="weights")
        b = tf.Variable(tf.zeros([n_neurons]), name="bias")
        Z = tf.matmul(X, W) + b
        if activation is not None:
            return activation(Z)
        else:
            return Z


def leaky_relu(z, name=None):
    return tf.maximum(0.01 * z, z, name=name)

def leaky_relu_with_small_constant(z, name=None):
    return tf.maximum(tf.constant(0.01, shape=[1]) * z, z, name=name)

with tf.name_scope("dnn"):
    hidden1 = neuron_layer(input, n_hidden1, name="hidden1", activation=leaky_relu)
    hidden2 = neuron_layer(hidden1, n_hidden2, name="hidden2", activation=leaky_relu_with_small_constant)
    logits = neuron_layer(hidden2, n_outputs, name="outputs") #, activation=tf.nn.sigmoid)

with tf.name_scope("loss"):
    xentropy = tf.nn.sparse_softmax_cross_entropy_with_logits(labels=y, logits=logits)
    loss = tf.reduce_mean(xentropy, name="loss")

with tf.name_scope("train"):
    optimizer = tf.train.GradientDescentOptimizer(learning_rate)
    training_op = optimizer.minimize(loss)

with tf.name_scope("eval"):
    correct = tf.nn.in_top_k(logits, y, 1)
    accuracy = tf.reduce_mean(tf.cast(correct, tf.float32))

init = tf.global_variables_initializer()
accuracy_summary = tf.summary.scalar('Accuracy', accuracy)
file_writer = tf.summary.FileWriter(logdir, tf.get_default_graph())

with tf.Session() as sess:
    init.run()
    for epoch in range(n_epochs):
        for iteration in range(mnist.train.num_examples // batch_size):
            X_batch, y_batch = mnist.train.next_batch(batch_size)
            sess.run(training_op, feed_dict={input: X_batch, y: y_batch})
        acc_train = accuracy.eval(feed_dict={input: X_batch, y: y_batch})
        acc_val = accuracy.eval(feed_dict={input: mnist.validation.images,
                                           y: mnist.validation.labels})
        print(epoch, "Train accuracy:", acc_train, "Val accuracy:", acc_val)

        # Save summary for tensorboard
        summary_str = accuracy_summary.eval(feed_dict={input: mnist.validation.images,
                                                       y: mnist.validation.labels})
        file_writer.add_summary(summary_str, epoch)

    export_path = "saved"
    print('Exporting trained model to ', export_path)
    builder = tf.saved_model.builder.SavedModelBuilder(export_path)
    signature = tf.saved_model.signature_def_utils.predict_signature_def(inputs = {'x':input}, outputs = {'y':logits})
    builder.add_meta_graph_and_variables(sess,
                                         [tf.saved_model.tag_constants.SERVING],
                                         signature_def_map={'serving_default':signature})
    builder.save(as_text=True)

file_writer.close()
