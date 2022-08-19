import { Button } from '@mantine/core';
import React from 'react';
import { errorMessage } from 'app/libs/notification';
import { Icon } from 'app/components';
import transform from 'app/pages/querybuilder/TransformVespaTrace';

// copied from safakeskin´s answer on SO, link: https://stackoverflow.com/questions/55613438/reactwrite-to-json-file-or-export-download-no-server
function downloadFile(filename, blob) {
  const href = URL.createObjectURL(blob);

  // create "a" HTML element with href to file
  const link = document.createElement('a');
  link.href = href;
  link.download = filename;
  document.body.appendChild(link);
  link.click();

  // clean up "a" element & remove ObjectURL
  document.body.removeChild(link);
  URL.revokeObjectURL(href);
}

export function DownloadJaeger({ response, ...props }) {
  const handleClick = () => {
    try {
      const json = JSON.parse(response);

      try {
        const content = JSON.stringify(transform(json), null, 4);
        downloadFile(
          'vespa-response.json',
          new Blob([content], { type: 'application/json' })
        );
      } catch (error) {
        errorMessage(
          'Request must be made with tracelevel ≥ 4',
          'Failed to transform response to Jaeger format'
        );
      }
    } catch (error) {
      errorMessage(error.message);
    }
  };

  return (
    <Button
      {...props}
      leftIcon={<Icon name="download" />}
      onClick={handleClick}
      disabled={!(response?.length > 0)}
    >
      Jaeger Format
    </Button>
  );
}
