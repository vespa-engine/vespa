import React from 'react';
import { showNotification } from '@mantine/notifications';
import { Icon } from 'app/components';
import { SHADE } from 'app/styles/theme/colors';

function getStyles(color) {
  return (theme) => ({
    root: {
      color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      background: theme.fn.themeColor(color, SHADE.UI_ELEMENT_BACKGROUND),
      borderColor: theme.fn.themeColor(
        color,
        SHADE.UI_ELEMENT_BORDER_AND_FOCUS
      ),
    },
    title: {
      fontWeight: 700,
      color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      '&:hover': {
        color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      },
    },
    description: {
      color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      '&:hover': {
        color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      },
    },
    closeButton: {
      color: theme.fn.themeColor(color, SHADE.LOW_CONTRAST_TEXT),
      '&:hover': {
        backgroundColor: theme.fn.themeColor(
          color,
          SHADE.HOVERED_UI_ELEMENT_BACKGROUND
        ),
      },
    },
  });
}

const commonMessage = ({ title, icon, color, message }) => {
  return showNotification({
    styles: getStyles(color),
    icon: <Icon name={icon} />,
    title,
    color,
    message,
  });
};

export const infoMessage = (
  message,
  title = 'Info',
  icon = 'info',
  color = 'blue'
) => commonMessage({ title, icon, color, message });

export const warningMessage = (
  message,
  title = 'Warning',
  icon = 'exclamation',
  color = 'orange'
) => commonMessage({ title, icon, color, message });

export const errorMessage = (
  message,
  title = 'Error',
  icon = 'xmark',
  color = 'red'
) => commonMessage({ title, icon, color, message });

export const successMessage = (
  message,
  title = 'Success',
  icon = 'check',
  color = 'green'
) => commonMessage({ title, icon, color, message });
