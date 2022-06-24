import React, { useContext } from 'react';
import { QueryInputContext } from '../Contexts/QueryInputContext';
import OverlayImageButton from '../Buttons/OverlayImageButton';

export default function AddQueryInput() {
  const { inputs, setInputs, id, setId } = useContext(QueryInputContext);

  const updateInputs = (e) => {
    e.preventDefault();
    setId((id) => id + 1);
    setInputs((prevInputs) => [
      ...prevInputs,
      { id: id + 1, type: '', input: '', hasChildren: false, children: [] },
    ]);
  };

  return (
    <OverlayImageButton
      onClick={updateInputs}
      className="addRow"
      id="addRow"
      tooltip="Add row"
      height="0"
      width="0"
    >
      +
    </OverlayImageButton>
  );
}
