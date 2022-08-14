import React from 'react';
import { library } from '@fortawesome/fontawesome-svg-core';
import { Box } from '@mantine/core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import {
  faArrowsToDot,
  faChartGantt,
  faCheck,
  faCircleMinus,
  faCopy,
  faDownload,
  faPaste,
  faPlus,
  faXmark,
  faInfo,
  faExclamation,
} from '@fortawesome/free-solid-svg-icons';

// TODO: use dynamic import

library.add(
  faArrowsToDot,
  faChartGantt,
  faCircleMinus,
  faPlus,
  faPaste,
  faDownload,
  faCopy,
  faCheck,
  faXmark,
  faInfo,
  faExclamation
);

export function Icon({ name, type = 'solid', color, ...rest }) {
  const icon = `fa-${type} fa-${name}`;
  return (
    <Box
      sx={(theme) => ({
        ...(color && { color: theme.cr.getSolidBackground(color) }),
      })}
      component={FontAwesomeIcon}
      icon={icon}
      {...rest}
    />
  );
}
