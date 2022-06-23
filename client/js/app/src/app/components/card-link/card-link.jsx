import React from 'react';
import { Box } from '@mantine/core';

export function CardLink({
  sx,
  withBorder = true,
  borderStyle = 'solid',
  height = '144px',
  width = '377px',
  ...props
}) {
  return (
    <Box
      sx={(theme) => ({
        width,
        height,
        display: 'grid',
        placeContent: 'center',
        ...theme.fn.hover({
          cursor: 'pointer',
          background: theme.cr.getSubtleBackground(),
          border: withBorder
            ? `1px ${borderStyle} ${theme.cr.getUiElementBorderAndFocus()}`
            : 0,
        }),
        border: withBorder
          ? `1px ${borderStyle} ${theme.cr.getSubtleBorderAndSeparator()}`
          : 0,
        ...sx,
      })}
      {...props}
    />
  );
}
