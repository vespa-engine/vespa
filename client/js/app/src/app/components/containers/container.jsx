// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import React from 'react';
import { Box } from '@mantine/core';

export function Container({ sx, ...props }) {
  return (
    <Box
      sx={() => ({
        display: 'grid',
        ...sx,
      })}
      {...props}
    />
  );
}
