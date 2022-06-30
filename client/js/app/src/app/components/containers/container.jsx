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
