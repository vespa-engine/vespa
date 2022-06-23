import React from 'react';
import { Paper, Stack, Box } from '@mantine/core';

export function Content({
  transparent,
  withBorder,
  padding,
  borderStyle = 'solid',
  stack = true,
  sx,
  ...props
}) {
  const Wrapper = stack ? Stack : Box;
  return (
    <Paper
      sx={(theme) => ({
        background: transparent && 'transparent',
        border: withBorder
          ? `1px ${borderStyle} ${theme.cr.getSubtleBorderAndSeparator()}`
          : 0,
      })}
    >
      <Wrapper
        sx={(theme) => ({
          padding: padding ?? theme.spacing.md,
          ...sx,
        })}
        {...props}
      />
    </Paper>
  );
}
