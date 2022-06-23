import React from 'react';
import { Box } from '@mantine/core';

export function CardLink({
  sx,
  withBorder = true,
  borderStyle = 'solid',
  minHeight = '89px',
  minWidth = 'auto',
  ...props
}) {
  return (
    <Box
      sx={(theme) => ({
        minHeight,
        minWidth,
        display: 'grid',
        placeContent: 'center',
        justifyItems: 'center',
        rowGap: '8px',
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
