import React, { useContext } from 'react';
import { ResponseContext } from '../Contexts/ResponseContext';
import transform from '../../TransformVespaTrace';
import SimpleButton from './SimpleButton';

export default function DownloadJSONButton() {
  const { response } = useContext(ResponseContext);

  const transformResponse = (response) => {
    return transform(response);
  };

  const handleClick = () => {
    if (response != '') {
      let transformedResponse = JSON.stringify(
        transformResponse(JSON.parse(response), undefined, '\t')
      );
      // taken from safakeskin´s answer on SO, link: https://stackoverflow.com/questions/55613438/reactwrite-to-json-file-or-export-download-no-server
      const fileName = 'vespa-response';
      const blob = new Blob([transformedResponse], {
        type: 'application/json',
      });
      const href = URL.createObjectURL(blob);

      // create "a" HTLM element with href to file
      const link = document.createElement('a');
      link.href = href;
      link.download = fileName + '.json';
      document.body.appendChild(link);
      link.click();

      // clean up "a" element & remove ObjectURL
      document.body.removeChild(link);
      URL.revokeObjectURL(href);
      window.open('http://localhost:16686/search', '__blank');
    } else {
      alert('Response was empty');
    }
  };

  return (
    <SimpleButton onClick={handleClick}>Download response as JSON</SimpleButton>
  );
}
