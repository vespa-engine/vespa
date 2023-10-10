// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import React from 'react';
import { Stack } from '@mantine/core';

export function Section({ transparent, sx, ...props }) {
  return (
    <Stack
      sx={(theme) => ({
        padding: theme.spacing.md,
        background: transparent
          ? 'transparent'
          : theme.cr.getSubtleBackground(),
        ...sx,
      })}
      {...props}
    />
  );
}
