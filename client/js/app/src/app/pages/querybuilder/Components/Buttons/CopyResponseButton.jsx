import React, { useContext } from 'react';
import OverlayImageButton from './OverlayImageButton';

import copyImage from '../../assets/img/copy.svg';
import { ResponseContext } from '../Contexts/ResponseContext';

export default function CopyResponseButton() {
  const { response } = useContext(ResponseContext);

  const handleCopy = () => {
    navigator.clipboard.writeText(response);
  };

  return (
    <OverlayImageButton
      className="intro-copy"
      image={copyImage}
      height="30"
      width="30"
      tooltip="Copy"
      onClick={handleCopy}
    />
  );
}
