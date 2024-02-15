package ai.vespa.search.llm;

import com.yahoo.processing.response.DefaultIncomingData;
import com.yahoo.search.result.DefaultErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import com.yahoo.search.result.Relevance;

public class TokenStream extends HitGroup {

    private int tokenCount = 0;

    private TokenStream(String id, DefaultIncomingData<Hit> incomingData) {
        super(id, new Relevance(1), incomingData);
        this.setOrdered(true);  // avoid hit group ordering - important for errors
    }

    public static TokenStream create(String id) {
        DefaultIncomingData<Hit> incomingData = new DefaultIncomingData<>();
        TokenStream stream = new TokenStream(id, incomingData);
        incomingData.assignOwner(stream);
        return stream;
    }

    public static HitGroup createAsync(String id) {
        return create(id);
    }

    public void add(String token) {
        incoming().add(new Token(String.valueOf(++tokenCount), token));
    }

    public void error(String source, ErrorMessage message) {
        incoming().add(new DefaultErrorHit(source, message));
    }

    public void markComplete() {
        incoming().markComplete();
    }

    public static class Token extends Hit {

        public Token(String token) {
            this("", token);
        }

        public Token(String id, String token) {
            super(id);
            setField("token", token);
        }

        public String toString() {
            return getField("token").toString();
        }

    }
}
