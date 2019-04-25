package com.yahoo.vespaxmlparser;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;

public abstract class FeedOperation {
    public enum Type {DOCUMENT, REMOVE, UPDATE, INVALID}

    private Type type;
    protected FeedOperation() {
        this(Type.INVALID);
    }
    protected FeedOperation(Type type) {
        this.type = type;
    }
    public final Type getType() { return type; }
    protected final void setType(Type type) {
        this.type = type;
    }

    public abstract Document getDocument();
    public abstract DocumentUpdate getDocumentUpdate();
    public abstract DocumentId getRemove();

    public TestAndSetCondition getCondition() {
        return TestAndSetCondition.NOT_PRESENT_CONDITION;
    }
    @Override
    public String toString() {
        return "Operation{" +
                "type=" + getType() +
                ", doc=" + getDocument() +
                ", remove=" + getRemove() +
                ", docUpdate=" + getDocumentUpdate() +
                " testandset=" + getCondition() +
                '}';
    }
}