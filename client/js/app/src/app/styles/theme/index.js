import { common } from 'app/styles/theme/common';
import { components } from 'app/styles/theme/components';
import { commonColors } from 'app/styles/theme/common-colors';

export const getTheme = () => {
  return { ...common, components, colors: commonColors };
};
