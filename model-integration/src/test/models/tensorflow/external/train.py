# Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import numpy as np
import tensorflow as tf
import tensorflow.keras.backend as K

from tensorflow.keras.layers import Input, Dense, concatenate
from tensorflow.keras.models import Model

input_user = Input(shape=(3,))
input_ad = Input(shape=(3,))

merged = concatenate([input_user, input_ad])
output_1 = Dense(64, activation='relu')(merged)
output_2 = Dense(64, activation='relu')(output_1)
predictions = Dense(1)(output_2)

model = Model(inputs=[input_user, input_ad], outputs=predictions)
model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['accuracy'])
model.summary()

SAMPLES = 1000
user_data = np.random.rand(SAMPLES,3)
ad_data = np.random.rand(SAMPLES,3)
labels = np.random.rand(SAMPLES,1)
print(user_data[:10])
print(ad_data[:10])
print(labels[:10])

model.fit([user_data, ad_data], labels, epochs=10, )  # starts training

user_data_sample1 = np.random.rand(1, 3)
ad_data_sample1 = np.random.rand(1, 3)

print("predicting for:")
print(user_data_sample1)
print(ad_data_sample1)
predictions = model.predict([user_data_sample1, ad_data_sample1])
print(predictions)

signature = tf.saved_model.signature_def_utils.predict_signature_def(
    inputs={'input1': model.inputs[0],'input2': model.inputs[1] }, outputs={'pctr': model.outputs[0]})

builder = tf.saved_model.builder.SavedModelBuilder('modelv1')
builder.add_meta_graph_and_variables(
    sess=K.get_session(),
    tags=[tf.saved_model.tag_constants.SERVING],
    signature_def_map={
        tf.saved_model.signature_constants.DEFAULT_SERVING_SIGNATURE_DEF_KEY:
            signature
    })
builder.save()
