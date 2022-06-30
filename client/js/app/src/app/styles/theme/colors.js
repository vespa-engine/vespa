export const SHADE = Object.freeze({
  APP_BACKGROUND: 0,
  SUBTLE_BACKGROUND: 1,
  UI_ELEMENT_BACKGROUND: 2,
  HOVERED_ELEMENT_BACKGROUND: 3,
  SUBTLE_BORDER_AND_SEPARATOR: 4,
  UI_ELEMENT_BORDER_AND_FOCUS: 5,
  SOLID_BACKGROUND: 6,
  HOVERED_SOLID_BACKGROUND: 7,
  LOW_CONTRAST_TEXT: 8,
  HIGH_CONTRAST_TEXT: 9,
});

export class Colors {
  constructor(theme) {
    this.theme = theme;
    this.themeColor = theme.colors['blue'];
  }

  getAppBackground() {
    return this.themeColor[SHADE.APP_BACKGROUND];
  }

  getSubtleBackground() {
    return this.themeColor[SHADE.SUBTLE_BACKGROUND];
  }

  getUiElementBackground() {
    return this.themeColor[SHADE.UI_ELEMENT_BACKGROUND];
  }

  getHoveredUiElementBackground() {
    return this.themeColor[SHADE.HOVERED_ELEMENT_BACKGROUND];
  }

  getSubtleBorderAndSeparator() {
    return this.themeColor[SHADE.SUBTLE_BORDER_AND_SEPARATOR];
  }

  getUiElementBorderAndFocus() {
    return this.themeColor[SHADE.UI_ELEMENT_BORDER_AND_FOCUS];
  }

  getSolidBackground(color) {
    return this.theme.fn.themeColor(color, SHADE.SOLID_BACKGROUND);
  }

  getHoveredSolidBackground(color) {
    return this.theme.fn.themeColor(color, SHADE.HOVERED_SOLID_BACKGROUND);
  }

  getLowContrastText() {
    return this.theme.colors.gray[SHADE.LOW_CONTRAST_TEXT];
  }

  getHighContrastText() {
    return this.theme.colors.gray[SHADE.HIGH_CONTRAST_TEXT];
  }

  getText(color, variant) {
    return color === 'dimmed'
      ? this.theme.fn.themeColor('gray', SHADE.SOLID_BACKGROUND)
      : color in this.theme.colors
      ? this.theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT)
      : variant === 'link'
      ? this.theme.fn.themeColor(color, SHADE.SOLID_BACKGROUND)
      : color || 'inherit';
  }
}
