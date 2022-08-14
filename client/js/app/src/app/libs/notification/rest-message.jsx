import {
  errorMessage,
  infoMessage,
  successMessage,
} from 'app/libs/notification/index';

export const restMessage = (response, prefix, code = 200) => {
  // Gracefully handle various types of rest responses input
  // Response can be a raw fetch request, an already decoded object or a plain string
  if (typeof response === 'object') {
    if (typeof response.text === 'function') {
      Promise.resolve(response.text()).then((text) => {
        restMessageContent(response.status || code, text, prefix);
      });
    } else {
      restMessageContent(
        response.code || 200,
        response.message || JSON.stringify(response),
        prefix
      );
    }
  } else if (typeof response === 'string') {
    restMessageContent(code, response, prefix);
  }
};

const restMessageContent = (code, message, prefix) => {
  // Trunk long messages
  if (message.length > 200) message = message.substring(0, 200) + '...';

  // Add pre-message if given
  // Most of the time the message is just "Success" - so you would like
  // to prefix it with eg. 'Adding user: '
  message = prefix ? prefix + message : message;

  if (code < 300) {
    successMessage(message);
  } else if (code < 400) {
    infoMessage(message);
  } else {
    errorMessage(message);
  }
};
