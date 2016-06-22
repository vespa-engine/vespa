#!/bin/bash
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
CALLER_GID=$(stat -c "%g" $DIR)

groupadd -f -g $CALLER_GID $USERNAME
useradd -u $CALLER_UID -g $CALLER_GID $USERNAME
echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

su -c "mkdir -p $DIR/../.ccache" $USERNAME
su -c "ln -sf $DIR/../.ccache /home/$USERNAME/.ccache" $USERNAME

su -c "mkdir -p $DIR/../.m2" $USERNAME
su -c "ln -sf $DIR/../.m2 /home/$USERNAME/.m2" $USERNAME

cd $DIR/.. 
su $USERNAME

