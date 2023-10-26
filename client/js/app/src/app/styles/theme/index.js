// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import { common } from 'app/styles/theme/common';
import { components } from 'app/styles/theme/components';
import { commonColors } from 'app/styles/theme/common-colors';

export const getTheme = () => {
  return { ...common, components, colors: commonColors };
};
