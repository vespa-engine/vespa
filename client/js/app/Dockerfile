FROM  node:lts-alpine

COPY . /app

WORKDIR /app

RUN yarn install && \
  yarn build

CMD [ "yarn", "preview", "--host" ]
