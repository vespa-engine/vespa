FROM gliderlabs/alpine:3.4

# Install python and curl
RUN apk-install python curl

# Copy source
ADD src/ server/src

# Run http server on port 80
EXPOSE  80
CMD ["python", "server/src/server.py"]
