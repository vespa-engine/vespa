export const styles = (theme) => ({
  '*, *::before, *::after': {
    boxSizing: 'border-box',
  },

  '*': {
    margin: '0',
  },

  html: {
    height: '100%',
  },

  body: {
    height: '100%',
    WebkitFontSmoothing: 'antialiased',
    lineHeight: theme.lineHeight,
    background: theme.cr.getAppBackground(),
    color: theme.cr.getHighContrastText(),
    ...theme.fn.fontStyles(),
  },

  'img, picture, video, canvas, svg': {
    display: 'block',
  },

  'input, button, textarea, select': {
    font: 'inherit',
  },

  'p, h1, h2, h3, h4, h5, h6': {
    overflowWrap: 'break-word',
  },

  '#root': {
    height: '100%',
    isolation: 'isolate',
  },
});
