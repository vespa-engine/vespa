package com.yahoo.language.significance;

import com.yahoo.language.Language;

public interface SignificanceModelRegistry {
    SignificanceModel getModel(Language language);
}
