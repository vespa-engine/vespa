import { common } from 'app/styles/theme/common';
import { commonColors } from 'app/styles/theme/common-colors';

export const getTheme = () => {
  return { ...common, colors: commonColors };
};
