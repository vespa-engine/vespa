#!/bin/bash
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

if [ $# -ne 0 ]; then
  echo "Usage: $0"
  echo "This script should not be called manually."
  exit 1
fi

USERNAME=builder
DIR=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd)
cd $DIR

CALLER_UID=$(stat -c "%u" $DIR)

if [ $CALLER_UID -ne 0 ]; then
  # We are in a system that maps the uids and this dir is not owned by root
  # Create a user with same uid and gid to avoid mixing
  CALLER_GID=$(stat -c "%g" $DIR)

  groupadd -f -g $CALLER_GID $USERNAME
  useradd -u $CALLER_UID -g $CALLER_GID $USERNAME
  echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
else
  USERNAME=root
fi

su -c "mkdir -p $DIR/../.ccache" - $USERNAME
su -c "ln -sf $DIR/../.ccache \$HOME/.ccache" - $USERNAME

su -c "mkdir -p $DIR/../.m2" - $USERNAME
su -c "ln -sf $DIR/../.m2 \$HOME/.m2" - $USERNAME

cd $DIR/.. 
su $USERNAME

