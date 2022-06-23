import React from 'react';
import { Box } from '@mantine/core';

export function Message({ sx, ...props }) {
  return (
    <Box
      sx={() => ({
        display: 'grid',
        placeContent: 'center',
        minHeight: '89px',
        ...sx,
      })}
      {...props}
    />
  );
}
