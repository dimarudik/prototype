package dev.bingo.spring;

import dev.bingo.provider.URLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestURLProvider implements URLProvider {

    @Override
    public String getURL(String url) {
        return url.replace("t-proxy:", "").replace("foo", "localhost");
    }

    @Override
    public boolean acceptsURL(String url) {
        return url.startsWith("jdbc:t-proxy:");
    }
}
