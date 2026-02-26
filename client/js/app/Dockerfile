FROM  node:lts-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114

COPY . /app

WORKDIR /app

RUN yarn install && \
  yarn build

CMD [ "yarn", "preview", "--host" ]
