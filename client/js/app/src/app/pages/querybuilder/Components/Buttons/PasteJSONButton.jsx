import React, { useState } from 'react';
import pasteImage from '../../assets/img/paste.svg';
import ImageButton from './ImageButton';
import {
  ACTION,
  dispatch,
} from 'app/pages/querybuilder/Components/Contexts/QueryBuilderProvider';

export default function PasteJSONButton() {
  const [paste, setPaste] = useState(false);

  const handleClick = () => {
    setPaste(true);
    window.addEventListener('paste', handlePaste);
  };

  const handlePaste = (e) => {
    setPaste(false);
    // Stop data actually being pasted into div
    e.stopPropagation();
    e.preventDefault();
    const pastedData = e.clipboardData.getData('text');
    window.removeEventListener('paste', handlePaste);
    dispatch(ACTION.SET_QUERY, pastedData);
  };

  return (
    <>
      <ImageButton
        className="pasteJSON"
        image={pasteImage}
        onClick={handleClick}
      >
        {paste ? 'Press CMD + V' : 'Paste JSON'}
      </ImageButton>
    </>
  );
}
