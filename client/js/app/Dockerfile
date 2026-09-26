# syntax=docker/dockerfile:1

# Build stage: install deps and produce static assets.
FROM node:lts-alpine@sha256:7fddd9ddeae8196abf4a3ef2de34e11f7b1a722119f91f28ddf1e99dcafdf114 AS builder

WORKDIR /app

COPY package.json yarn.lock ./
RUN yarn install --frozen-lockfile --non-interactive

COPY . .
RUN yarn build

# Runtime stage: tiny nginx serving the built SPA.
FROM nginx:1.31-alpine-slim@sha256:f761b94f2cb9e8e05e2943d5f773609596113ef69b54e2433a996d109a8f78b7

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 3000
