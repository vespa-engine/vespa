# Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import numpy as np
import tensorflow as tf
import tensorflow.keras.backend as K

from tensorflow.keras.layers import Input, Dense, concatenate, Embedding, Reshape
from tensorflow.keras.models import Model

input_user = Input(shape=(3,))
input_ad = Input(shape=(3,))
gender_samples = Input(shape=(1,), dtype='int32')

gender_values = ['m', 'f', 'a']

gender_embeddings = Embedding(len(gender_values), 1)(gender_samples)
reshape_gender = Reshape(target_shape=[1])(gender_embeddings)

model2 = Model(inputs=[gender_samples], outputs=reshape_gender)
model2.summary()

merged = concatenate([input_user, input_ad, reshape_gender])
output_1 = Dense(64, activation='relu')(merged)
output_2 = Dense(64, activation='relu')(output_1)
predictions = Dense(1)(output_2)

model = Model(inputs=[input_user, input_ad, gender_samples], outputs=predictions)
model.compile(optimizer='adam',
              loss='binary_crossentropy',
              metrics=['accuracy'])
model.summary()

SAMPLES = 1000
user_data = np.random.rand(SAMPLES,3)
ad_data = np.random.rand(SAMPLES,3)
gender_data = np.random.randint(len(gender_values), size=SAMPLES)
labels = np.random.rand(SAMPLES,1)
print(user_data[:10])
print(ad_data[:10])
print(gender_data[:10])
print(labels[:10])

model.fit([user_data, ad_data, gender_data], labels, epochs=10, )  # starts training

user_data_sample1 = np.random.rand(1, 3)
ad_data_sample1 = np.random.rand(1, 3)
gender_data_sample1 = np.random.randint(len(gender_values), size=1)

print("predicting for:")
print(user_data_sample1)
print(ad_data_sample1)
print(gender_data_sample1)
predictions = model.predict([user_data_sample1, ad_data_sample1, gender_data_sample1])
print(predictions)

signature = tf.saved_model.signature_def_utils.predict_signature_def(
    inputs={'input1': model.inputs[0],'input2': model.inputs[1], 'input3': model.inputs[2] }, outputs={'pctrx': model.outputs[0]})

builder = tf.saved_model.builder.SavedModelBuilder('modelv2')
builder.add_meta_graph_and_variables(
    sess=K.get_session(),
    tags=[tf.saved_model.tag_constants.SERVING],
    signature_def_map={
        tf.saved_model.signature_constants.DEFAULT_SERVING_SIGNATURE_DEF_KEY:
            signature
    })
builder.save()
