import { SHADE } from 'app/styles/theme/colors';

const inputSizes = Object.freeze({
  xs: 28,
  sm: 34,
  md: 40,
  lg: 48,
  xl: 58,
});

const buttonSizes = Object.freeze({
  xs: { height: inputSizes.xs, padding: '0 13px' },
  sm: { height: inputSizes.sm, padding: '0 17px' },
  md: { height: inputSizes.md, padding: '0 21px' },
  lg: { height: inputSizes.lg, padding: '0 26px' },
  xl: { height: inputSizes.xl, padding: '0 34px' },
  'compact-xs': { height: 'initial', padding: '2px 5px' },
  'compact-sm': { height: 'initial', padding: '3px 8px' },
  'compact-md': { height: 'initial', padding: '5px 8px' },
  'compact-lg': { height: 'initial', padding: '5px 13px' },
  'compact-xl': { height: 'initial', padding: '8px 13px' },
});

export const segmentedControlSizes = Object.freeze({
  xs: '1px 8px',
  sm: '1px 13px',
  md: '3px 13px',
  lg: '5px 21px',
  xl: '8px 21px',
});

const titleStyles = Object.freeze({
  h1: {
    wordBreak: 'break-word',
    lineHeight: 'calc(1.5 / var(--space) * var(--vspace))',
  },
  h2: {
    wordBreak: 'break-word',
    fontWeight: 'normal',
    lineHeight: '1rem',
  },
  h3: {
    fontWeight: 'normal',
    textTransform: 'uppercase',
    letterSpacing: '0.1rem',
    lineHeight: '1rem',
  },
  h4: {
    textTransform: 'capitalize',
    lineHeight: 'var(--vspace)',
  },
  h5: {
    fontWeight: 'lighter',
    textTransform: 'uppercase',
    letterSpacing: '0.15rem',
    lineHeight: 'var(--vspace)',
  },
  h6: {
    fontWeight: 'normal',
    fontStyle: 'italic',
    letterSpacing: '0 !important',
    lineHeight: 'var(--vspace)',
  },
});

const getButtonStyles = ({ compact, size }) => {
  if (!compact) return buttonSizes[size];
  return buttonSizes[`compact-${size}`];
};

const getTitleStyles = ({ element = 'h1' }) => titleStyles[element];

export function getVariantStyles({ fn, white }, color, variant) {
  if (variant === 'hover' || variant === 'transparent') {
    return {
      color: fn.themeColor(color, SHADE.SOLID_BACKGROUND),
      backgroundColor: 'transparent',
      border: '1px solid transparent',
      ...fn.hover(
        variant === 'hover'
          ? {
              backgroundColor: fn.themeColor(
                color,
                SHADE.UI_ELEMENT_BACKGROUND
              ),
            }
          : {}
      ),
    };
  }
  if (variant === 'light') {
    return {
      color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      backgroundColor: fn.themeColor(color, SHADE.HOVERED_ELEMENT_BACKGROUND),
      border: '1px solid transparent',
      '&:hover': {
        color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
        backgroundColor: fn.themeColor(
          color,
          SHADE.SUBTLE_BORDER_AND_SEPARATOR
        ),
      },
    };
  }
  if (variant === 'outline') {
    return {
      color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      backgroundColor: fn.themeColor(color, SHADE.APP_BACKGROUND),
      border: `1px solid ${fn.themeColor(color, SHADE.SOLID_BACKGROUND)}`,
      '&:hover': {
        color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
        borderColor: fn.themeColor(color, SHADE.HOVERED_SOLID_BACKGROUND),
      },
    };
  }
  if (variant === 'dot') {
    return {
      color: fn.themeColor('gray', SHADE.LOW_CONTRAST_TEXT),
      backgroundColor: 'transparent',
      border: `1px solid ${fn.themeColor(
        'gray',
        SHADE.UI_ELEMENT_BORDER_AND_FOCUS
      )}`,
      '&:hover': {
        color: fn.themeColor('gray', SHADE.LOW_CONTRAST_TEXT),
        borderColor: fn.themeColor('gray', SHADE.UI_ELEMENT_BORDER_AND_FOCUS),
      },
      '&::before': {
        backgroundColor: fn.themeColor(color, SHADE.SOLID_BACKGROUND),
      },
    };
  }
  if (variant === 'subtle') {
    return {
      color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      backgroundColor: 'transparent',
      border: 'transparent',
      '&:hover': {
        color: fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
        backgroundColor: fn.themeColor(color, SHADE.HOVERED_ELEMENT_BACKGROUND),
      },
    };
  }
  if (variant === 'default') {
    return {
      color: fn.themeColor('gray', SHADE.HIGH_CONTRAST_TEXT),
      backgroundColor: fn.themeColor('gray', SHADE.SUBTLE_BACKGROUND),
      border: `1px solid ${fn.themeColor(
        'gray',
        SHADE.UI_ELEMENT_BORDER_AND_FOCUS
      )}`,
      '&:hover': {
        color: fn.themeColor('gray', SHADE.HIGH_CONTRAST_TEXT),
        backgroundColor: fn.themeColor(
          'gray',
          SHADE.HOVERED_ELEMENT_BACKGROUND
        ),
      },
    };
  }
  if (variant === 'basic') {
    return {
      color: fn.themeColor('gray', SHADE.HIGH_CONTRAST_TEXT),
      backgroundColor: 'transparent',
      border: '1px solid transparent',
      '&:hover': {
        color: fn.themeColor('gray', SHADE.HIGH_CONTRAST_TEXT),
        backgroundColor: 'transparent',
      },
    };
  }
  return {
    color: white,
    backgroundColor: fn.themeColor(color, SHADE.SOLID_BACKGROUND),
    border: '1px solid transparent',
    '&:hover': {
      color: white,
      backgroundColor: fn.themeColor(color, SHADE.HOVERED_SOLID_BACKGROUND),
    },
  };
}

function getInputSizes({ fn, size }) {
  return {
    minHeight: fn.size({ size, sizes: inputSizes }),
    height: fn.size({ size, sizes: inputSizes }),
    lineHeight: `${fn.size({ size, sizes: inputSizes }) - 2}px`,
  };
}

function getInputVariantStyles({ fn, cr }, variant, size) {
  if (variant === 'unstyled') {
    return {
      color: cr.getHighContrastText(),
    };
  }
  if (variant === 'filled') {
    return {
      backgroundColor: cr.getUiElementBackground(),
      '&:focus, &:focus-within': {
        borderColor: `${cr.getUiElementBorderAndFocus()} !important`,
      },
      ...getInputSizes({ fn, size }),
    };
  }
  return {
    border: `1px solid ${cr.getSubtleBorderAndSeparator()}`,
    backgroundColor: cr.getSubtleBackground(),
    '&:focus, &:focus-within': {
      borderColor: cr.getUiElementBorderAndFocus(),
    },
    ...getInputSizes({ fn, size }),
  };
}

export const defaultStyles = {
  AppShell: () => ({
    main: {
      maxWidth: '1920px',
      margin: '0 auto',
    },
  }),
  ActionIcon: (theme, { color }) => ({
    root: {
      '&:disabled': {
        color: theme.fn.themeColor('gray', SHADE.SOLID_BACKGROUND),
        backgroundColor: 'transparent',
        borderColor: 'transparent',
        opacity: 0.55,
      },
    },
    light: getVariantStyles(theme, color, 'light'),
    filled: getVariantStyles(theme, color, 'filled'),
    outline: getVariantStyles(theme, color, 'outline'),
    default: getVariantStyles(theme, color, 'default'),
    hover: getVariantStyles(theme, color, 'hover'),
    transparent: getVariantStyles(theme, color, 'transparent'),
  }),
  Badge: (theme, { color }) => ({
    light: getVariantStyles(theme, color, 'light'),
    filled: getVariantStyles(theme, color, 'filled'),
    outline: getVariantStyles(theme, color, 'outline'),
    dot: getVariantStyles(theme, color, 'dot'),
  }),
  Button: (theme, { color, compact, size }) => ({
    root: getButtonStyles({ compact, size }),
    light: getVariantStyles(theme, color, 'light'),
    filled: getVariantStyles(theme, color, 'filled'),
    outline: getVariantStyles(theme, color, 'outline'),
    subtle: getVariantStyles(theme, color, 'subtle'),
    default: getVariantStyles(theme, color, 'default'),
    leftIcon: { marginRight: 3 },
    rightIcon: { marginLeft: 3 },
  }),
  Divider: (theme) => ({
    horizontal: {
      borderTopColor: theme.cr.getSubtleBorderAndSeparator(),
    },
  }),
  Input: (theme, { invalid, size }) => ({
    defaultVariant: { ...getInputVariantStyles(theme, 'default', size) },
    filledVariant: { ...getInputVariantStyles(theme, 'filled', size) },
    unstyledVariant: { ...getInputVariantStyles(theme, 'unstyled', size) },
    invalid: {
      color: theme.fn.themeColor('red', SHADE.SOLID_BACKGROUND),
      borderColor: theme.fn.themeColor('red', SHADE.SOLID_BACKGROUND),
      '&::placeholder': {
        color: theme.fn.themeColor('red', SHADE.SOLID_BACKGROUND),
      },
    },
    disabled: {
      backgroundColor: theme.fn.themeColor('gray', SHADE.SOLID_BACKGROUND),
      color: theme.fn.themeColor('gray', SHADE.LOW_CONTRAST_TEXT),
      '&::placeholder': {
        color: theme.fn.themeColor('gray', SHADE.LOW_CONTRAST_TEXT),
      },
    },
    icon: {
      color: invalid
        ? theme.fn.themeColor('red', SHADE.SOLID_BACKGROUND)
        : theme.cr.getUiElementBorderAndFocus(),
    },
  }),
  Paper: (theme) => ({
    root: {
      color: theme.cr.getHighContrastText(),
      backgroundColor: theme.cr.getUiElementBackground(),
    },
  }),
  Popover: (theme) => ({
    arrow: {
      borderColor: theme.cr.getUiElementBorderAndFocus(),
      background: theme.cr.getSubtleBackground(),
    },
    popover: {
      background: theme.cr.getSubtleBackground(),
    },
    body: {
      border: `1px solid ${theme.cr.getUiElementBorderAndFocus()}`,
      whiteSpace: 'nowrap',
    },
    header: {
      borderBottom: `1px solid ${theme.cr.getUiElementBorderAndFocus()}`,
    },
  }),
  Progress: (theme) => ({
    root: {
      display: 'flex',
      backgroundColor: theme.fn.themeColor(
        'gray',
        SHADE.SUBTLE_BORDER_AND_SEPARATOR
      ),
    },
    bar: {
      position: 'unset',
      minWidth: '2em',
    },
  }),
  SegmentedControl: (theme, { size }) => ({
    root: {
      backgroundColor: theme.cr.getUiElementBackground(),
    },
    label: {
      color: theme.cr.getLowContrastText(),
      padding:
        segmentedControlSizes[size in segmentedControlSizes ? size : 'sm'],
    },
    control: {
      '&:not(:first-of-type)': {
        borderColor: theme.cr.getUiElementBorderAndFocus(),
      },
    },
  }),
  Select: (theme, { variant, size }) => ({
    input: { ...getInputVariantStyles(theme, variant, size) },
    dropdown: {
      backgroundColor: theme.cr.getSubtleBackground(),
      border: `1px solid ${theme.cr.getUiElementBorderAndFocus()}`,
      borderRadius: theme.radius.xs,
    },
    item: {
      color: theme.cr.getHighContrastText(),
      borderRadius: 0,
    },
    selected: {
      backgroundColor: theme.cr.getUiElementBackground(),
      color: theme.cr.getHighContrastText(),
    },
    hovered: {
      backgroundColor: theme.cr.getHoveredUiElementBackground(),
    },
  }),
  Table: (theme) => ({
    root: {
      color: theme.cr.getHighContrastText(),
      '& thead tr th': {
        textTransform: 'uppercase',
        fontSize: theme.fontSizes.xs,
        color: theme.cr.getLowContrastText(),
        borderBottom: `1px solid ${theme.cr.getSubtleBorderAndSeparator()}`,
      },
      '& tbody tr td': {
        borderBottom: `1px solid ${theme.cr.getSubtleBorderAndSeparator()}`,
      },
      '& thead tr:hover': {
        backgroundColor: 'transparent !important',
      },
    },
  }),
  Text: (theme, { color, variant }) => ({
    root: {
      color: theme.cr.getText(color, variant),
      ...theme.fn.hover({
        color: theme.cr.getText(color, variant),
        textDecorationColor: theme.fn.themeColor(
          color,
          SHADE.UI_ELEMENT_BORDER_AND_FOCUS
        ),
      }),
    },
  }),
  Title: (theme, { element }) => ({
    root: {
      '--space': theme.lineHeight,
      '--vspace': 'calc(var(--space) * 1rem)',
      ...getTitleStyles({ element }),
    },
  }),
  Tooltip: (theme) => ({
    body: { ...theme.cr.getTooltip() },
    arrow: { ...theme.cr.getTooltip() },
  }),
};
