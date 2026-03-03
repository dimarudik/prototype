package dev.bingo.provider;

import static dev.bingo.Constant.PREFIX;

public class DefaultURLProvider implements URLProvider {

    @Override
    public String getURL(String url) {
        return url.replace(PREFIX, "jdbc:");
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }
}
