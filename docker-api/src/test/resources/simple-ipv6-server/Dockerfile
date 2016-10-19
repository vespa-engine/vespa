# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

FROM gliderlabs/alpine:3.4

# Install python and curl
RUN apk-install python curl

# Copy source
ADD src/ pysrc

# Run http server on port 80
EXPOSE  80
CMD ["python", "/pysrc/server.py"]
