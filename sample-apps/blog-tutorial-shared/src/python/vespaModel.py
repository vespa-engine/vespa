#! /Users/tmartins/anaconda/envs/tensorflow/bin/python
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

"""
Train a 2 layers neural network to compute the probability of a user 
represented by the vector u liking a document represented by the vector d.

Usage: ./vespaModel.py --product_features_file_path path \
                       --user_features_file_path path \
                       --dataset_file_path path

Expected File formats:

- product_features_file_path contains a file with rows following the JSON format below:

{"post_id" : 20, 
 "user_item_cf" : {"user_item_cf:5" : -0.66617566, 
                   "user_item_cf:6" : 0.29197264, 
                   "user_item_cf:1" : -0.15582734, 
                   "user_item_cf:7" : 0.3350679, 
                   "user_item_cf:2" : -0.16676047, 
                   "user_item_cf:9" : -0.31653953, 
                   "user_item_cf:3" : -0.21495385, 
                   "user_item_cf:4" : -0.036676258, 
                   "user_item_cf:8" : 0.122069225, 
                   "user_item_cf:0" : 0.20922394}}

- user_features_file_path contains a file with rows following the JSON format below:

{"user_id" : 270, 
 "user_item_cf" : {"user_item_cf:5" : -0.54011273, 
                   "user_item_cf:6" : 0.2723072, 
                   "user_item_cf:1" : -0.23280832, 
                   "user_item_cf:7" : -0.011183357, 
                   "user_item_cf:2" : -0.3987285, 
                   "user_item_cf:9" : -0.05703937, 
                   "user_item_cf:3" : 0.04699418, 
                   "user_item_cf:4" : 0.06679048, 
                   "user_item_cf:8" : 0.31399783, 
                   "user_item_cf:0" : 0.5000366}}

- dataset_file_path contains a file with rows containing tab-separated post_id, user_id, label such as the sample below:

1000054	    118475	1
10001560	666315	0
10001560	1230226	0
10001560	561306	1
"""


import tensorflow as tf
import time
import os
import datetime
import json
import numpy as np

class getData:
    """
    Data pre-processing
    """
    def __init__(self, product_features_file_path, user_features_file_path, data_set_file_path):
        self.product_features_file_path = product_features_file_path
        self.user_features_file_path = user_features_file_path
        self.data_set_file_path = data_set_file_path

    # Create user and document lookup features
    def parse_cf_features(self, json, id_name):
        id = json[id_name]
        indexes = ['user_item_cf:' + str(x) for x in range(0,10,1)]
        values = [json['user_item_cf'][x] for x in indexes]
        return [id, values]
        
    def get_product_features_lookup(self):
        product_features = [self.parse_cf_features(json.loads(line), 'post_id') for line in open(self.product_features_file_path)]
        return dict(product_features)

    def get_user_features_lookup(self):
        user_features = [self.parse_cf_features(json.loads(line), 'user_id') for line in open(self.user_features_file_path)]
        return dict(user_features)

    def parse_dataset(self, line, lookup_user_features, lookup_product_features):
        info = line.strip("\n").split("\t")
        user_id = float(info[0])
        product_id = float(info[1])
        label = int(info[2])
        return lookup_user_features[user_id], lookup_product_features[product_id], [label]

    def prepare_dataset(self):
        lookup_product_features = self.get_product_features_lookup()
        lookup_user_features = self.get_user_features_lookup()
        with open(self.data_set_file_path) as f:
            input_u = []; input_d = []; input_y = []
            for line in f:
                u, d, y = self.parse_dataset(line, lookup_user_features, lookup_product_features)
                input_u.append(u)
                input_d.append(d)
                input_y.append(y)
        input_u = np.array(input_u)
        input_d = np.array(input_d)
        input_y = np.array(input_y)
        return input_u, input_d, input_y

    def create_train_test_sets(self, input_u, input_d, input_y, seed = 10, perc = 0.2):
        # Randomly shuffle data
        np.random.seed(seed)
        shuffle_indices = np.random.permutation(np.arange(len(input_u)))
        input_u_shuffled = input_u[shuffle_indices]
        input_d_shuffled = input_d[shuffle_indices]
        input_y_shuffled = input_y[shuffle_indices]
        
        # Split train/test set
        dev_samples = int(len(input_u_shuffled)*perc)
        u_train, u_dev = input_u_shuffled[:-dev_samples], input_u_shuffled[-dev_samples:]
        d_train, d_dev = input_d_shuffled[:-dev_samples], input_d_shuffled[-dev_samples:]
        y_train, y_dev = input_y_shuffled[:-dev_samples], input_y_shuffled[-dev_samples:]
        print("Train/Dev split: {:d}/{:d}".format(len(y_train), len(y_dev)))
        
        return u_train, u_dev, d_train, d_dev, y_train, y_dev        

    def batch_iter(self, data, batch_size, num_epochs, shuffle=True):
        """
        Generates a batch iterator for a dataset.
        """
        data = np.array(data)
        data_size = len(data)
        num_batches_per_epoch = int(len(data)/batch_size) + 1
        for epoch in range(num_epochs):
            # Shuffle the data at each epoch
            if shuffle:
                shuffle_indices = np.random.permutation(np.arange(data_size))
                shuffled_data = data[shuffle_indices]
            else:
                shuffled_data = data
            for batch_num in range(num_batches_per_epoch):
                start_index = batch_num * batch_size
                end_index = min((batch_num + 1) * batch_size, data_size)
                yield shuffled_data[start_index:end_index]

class vespaRunTimeModel:
    """
    Model that combine user and document features and needs to be evaluated at query time.
    """    
    def __init__(self, user_feature_length, doc_feature_length, hidden_length):

        # placeholders
        self.input_u = tf.placeholder(tf.float32, [None, user_feature_length], name = 'input_u')
        self.input_d = tf.placeholder(tf.float32, [None, doc_feature_length], name = 'input_d')
        self.input_y = tf.placeholder(tf.float32, [None, 1], name = 'input_y')

        # merge user and document vector
        self.input_concat = tf.concat(1, [self.input_d, self.input_u], name = 'input_concat')

        # hidden layer
        self.W_hidden = tf.Variable(
            tf.truncated_normal([user_feature_length + 
                doc_feature_length, hidden_length], stddev=0.1), name = 'W_hidden')
        self.b_hidden = tf.Variable(tf.constant(0.1, shape=[hidden_length]), name = 'b_hidden')

        self.hidden_layer = tf.nn.relu(tf.matmul(self.input_concat, self.W_hidden) + self.b_hidden, 
            name = 'hidden_layer')

        # output layer
        self.W_final = tf.Variable(
                tf.random_uniform([hidden_length, 1], -0.1, 0.1),
                name="W_final")
        self.b_final = tf.Variable(tf.zeros([1]), name="b_final")

        self.y = tf.sigmoid(tf.matmul(self.hidden_layer, self.W_final) + self.b_final, name = 'y')

        # prediction based on model output
        self.prediction = tf.cast(tf.greater_equal(self.y, 0.5), "float", name = 'prediction')

        # loss function
        prob = tf.clip_by_value(self.y,1e-5,1.0 - 1e-5)
        self.loss = tf.reduce_mean(- self.input_y * tf.log(prob) - (1 - self.input_y) * tf.log(1 - prob), name = 'loss')

        # accuracy
        correct_predictions = tf.equal(self.prediction, self.input_y)
        self.accuracy = tf.reduce_mean(tf.cast(correct_predictions, "float"), name="accuracy")

    def train_operation(self, learning_rate):
        global_step = tf.Variable(0, name="global_step", trainable=False)
        #optimizer = tf.train.GradientDescentOptimizer(learning_rate)
        optimizer = tf.train.AdagradOptimizer(learning_rate)
        train_op = optimizer.minimize(self.loss, global_step=global_step)
        return train_op, global_step

    def create_output_dir(self):
        timestamp = str(int(time.time()))
        out_dir = os.path.abspath(os.path.join(os.path.curdir, "runs", timestamp))
        print("Writing to {}\n".format(out_dir))
        return out_dir

    def summary_oprations(self):
        loss_summary = tf.scalar_summary("loss", self.loss)
        acc_summary = tf.scalar_summary("accuracy", self.accuracy)
        train_summary_op = tf.merge_summary([loss_summary, acc_summary])
        dev_summary_op = tf.merge_summary([loss_summary, acc_summary])
        return train_summary_op, dev_summary_op

    def train_step(self, u_batch, d_batch, y_batch, writer=None):
        """
        A single training step
        """
        feed_dict = {
          self.input_u: u_batch,
          self.input_d: d_batch,
          self.input_y: y_batch
        }
        _, step, summaries, loss, accuracy = sess.run(
            [train_op, global_step, train_summary_op, self.loss, self.accuracy],
            feed_dict)
        time_str = datetime.datetime.now().isoformat()
        print("{}: step {}, loss {:g}, acc {:g}".format(time_str, step, loss, accuracy))
        if writer:
            writer.add_summary(summaries, step)

    def dev_step(self, u_batch, d_batch, y_batch, writer=None):
        """
        Evaluates model on a dev set
        """
        feed_dict = {
          self.input_u: u_batch,
          self.input_d: d_batch,
          self.input_y: y_batch
        }
        step, summaries, loss, accuracy = sess.run(
            [global_step, dev_summary_op, self.loss, self.accuracy],
            feed_dict)
        time_str = datetime.datetime.now().isoformat()
        print("{}: step {}, loss {:g}, acc {:g}".format(time_str, step, loss, accuracy))
        if writer:
            writer.add_summary(summaries, step)

class serializeVespaModel:
    """
    Serialize TensorFlow variables to Vespa JSON format

    Example:    
        checkpoint_dir = "./runs/1473845959/checkpoints"
        output_dir = "./runs/1473845959/vespa_variables"
        
        serializer = serializeVespaModel(checkpoint_dir, output_dir)
        serializer.serialize_to_disk(variable_name = "W_hidden", dimension_names = ['input', 'hidden'])
        serializer.serialize_to_disk(variable_name = "b_hidden", dimension_names = ['hidden'])
        serializer.serialize_to_disk(variable_name = "W_final", dimension_names = ['hidden', 'final'])
        serializer.serialize_to_disk(variable_name = "b_final", dimension_names = ['final'])
    """
    def __init__(self, checkpoint_dir, output_dir):
        self.checkpoint_file = tf.train.latest_checkpoint(checkpoint_dir)
        self.reader = tf.train.NewCheckpointReader(self.checkpoint_file)
        self.output_dir = output_dir

    def write_cell_value(self, variable, dimension_names, dimension_address = None):
        if dimension_address is None:
            dimension_address = []
        shape = variable.shape
        if len(shape) == 1:
            count = 0
            cells = []
            for element in variable:
                dimension_address.append((dimension_names[0], str(count)))
                count += 1
                cells.append({ 'address': dict(dimension_address), "value": float(element) })                
            return cells
        else:
            count = 0
            output = []
            for slice in variable:
                dimension_address.append((dimension_names[0], str(count)))
                output.extend(self.write_cell_value(slice, dimension_names[1:], dimension_address))
                count += 1
            return output

    def write_to_vespa_json_format(self, variable_name, dimension_names):
        variable = self.reader.get_tensor(variable_name)
        cells = self.write_cell_value(variable, dimension_names)
        return json.dumps({'cells': cells})
        
    def serialize_to_disk(self, variable_name, dimension_names):
        text_file = open(os.path.join(output_dir, variable_name + ".json"), "w")
        text_file.write(serializer.write_to_vespa_json_format(variable_name, dimension_names))
        text_file.close()


def task_train():
    # Data 
    tf.flags.DEFINE_string("product_features_file_path", '', "File containing product features")
    tf.flags.DEFINE_string("user_features_file_path", '', "File containing user features")
    tf.flags.DEFINE_string("dataset_file_path", '', "File containing labels for each document user pair")

    tf.flags.DEFINE_integer("hidden_length_factor", 2, "The hidden layer has size 'hidden_length_factor * input_vector_length'")

    # Misc Parameters
    tf.flags.DEFINE_boolean("allow_soft_placement", True, "Allow device soft device placement")
    tf.flags.DEFINE_boolean("log_device_placement", False, "Log placement of ops on devices")

    # Training parameters
    tf.flags.DEFINE_float("learning_rate", 0.1, "Gradient Descent learning rate")

    tf.flags.DEFINE_integer("batch_size", 64, "Batch Size (default: 64)")
    tf.flags.DEFINE_integer("num_epochs", 200, "Number of training epochs (default: 200)")
    tf.flags.DEFINE_integer("evaluate_every", 100, "Evaluate model on dev set after this many steps (default: 100)")
    tf.flags.DEFINE_integer("checkpoint_every", 100, "Save model after this many steps (default: 100)")

    FLAGS = tf.flags.FLAGS
    FLAGS._parse_flags()
    print("\nParameters:")
    for attr, value in sorted(FLAGS.__flags.items()):
        print("{}={}".format(attr.upper(), value))
    print("")

    # Data preparation
    data_pre_processing = getData(
        FLAGS.product_features_file_path,
        FLAGS.user_features_file_path,
        FLAGS.dataset_file_path)
    
    input_u, input_d, input_y = data_pre_processing.prepare_dataset()
    u_train, u_dev, d_train, d_dev, y_train, y_dev = data_pre_processing.create_train_test_sets(input_u, input_d, input_y, seed = 10, perc = 0.2)

    user_feature_length = input_u.shape[1]
    doc_feature_length = input_d.shape[1]
    

    # Create a graph
    with tf.Graph().as_default():

        # Create a session
        session_conf = tf.ConfigProto(
          allow_soft_placement=FLAGS.allow_soft_placement,
          log_device_placement=FLAGS.log_device_placement)
        sess = tf.Session(config=session_conf)
        with sess.as_default():    

            # instanciate a model
            vespa_model = vespaRunTimeModel(user_feature_length = user_feature_length, 
                doc_feature_length = doc_feature_length, 
                hidden_length = FLAGS.hidden_length_factor * (user_feature_length + doc_feature_length))

            # create a train operation
            train_op, global_step = vespa_model.train_operation(learning_rate = FLAGS.learning_rate)

            # Summaries for loss and accuracy
            train_summary_op, dev_summary_op = vespa_model.summary_oprations()
            
            # Output directory for models and summaries
            out_dir = vespa_model.create_output_dir()

            # Write train summaries to disk
            train_summary_dir = os.path.join(out_dir, "summaries", "train")
            train_summary_writer = tf.train.SummaryWriter(train_summary_dir, sess.graph)

            # Dev summaries
            dev_summary_dir = os.path.join(out_dir, "summaries", "dev")
            dev_summary_writer = tf.train.SummaryWriter(dev_summary_dir, sess.graph)

            # Checkpoint directory. Tensorflow assumes this directory already exists so we need to create it
            checkpoint_dir = os.path.abspath(os.path.join(out_dir, "checkpoints"))
            checkpoint_prefix = os.path.join(checkpoint_dir, "model")
            if not os.path.exists(checkpoint_dir):
                os.makedirs(checkpoint_dir)
            saver = tf.train.Saver(tf.all_variables())

            # Initialize all variables
            sess.run(tf.initialize_all_variables())

        # Generate batches
        batches = data_pre_processing.batch_iter(
            list(zip(u_train, d_train, y_train)), FLAGS.batch_size, FLAGS.num_epochs)
        # Training loop. For each batch...
        for batch in batches:
            u_batch, d_batch, y_batch = zip(*batch)
            vespa_model.train_step(u_batch, d_batch, y_batch, writer=train_summary_writer)
            current_step = tf.train.global_step(sess, global_step)
            if current_step % FLAGS.evaluate_every == 0:
                print("\nEvaluation:")
                vespa_model.dev_step(u_dev, d_dev, y_dev, writer=dev_summary_writer)
                print("")
            if current_step % FLAGS.checkpoint_every == 0:
                path = saver.save(sess, checkpoint_prefix, global_step=current_step)
                print("Saved model checkpoint to {}\n".format(path))            

if __name__ == "__main__":

    # Task
    tf.flags.DEFINE_string("task", 'train', "Train a model from scratch")

    FLAGS = tf.flags.FLAGS
    FLAGS._parse_flags()
    print("\nParameters:")
    for attr, value in sorted(FLAGS.__flags.items()):
        print("{}={}".format(attr.upper(), value))
    print("")

    if FLAGS.task == "train":
        task_train()
