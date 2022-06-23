import React from 'react';
import { library } from '@fortawesome/fontawesome-svg-core';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowsToDot, faChartGantt } from '@fortawesome/free-solid-svg-icons';

library.add(faArrowsToDot, faChartGantt);

export function Icon({ name, type = 'solid', ...rest }) {
  const icon = `fa-${type} fa-${name}`;
  return <FontAwesomeIcon icon={icon} {...rest} />;
}
