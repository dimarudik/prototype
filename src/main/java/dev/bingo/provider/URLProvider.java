package dev.bingo.provider;

public interface URLProvider {
    String getURL(String url);
    boolean acceptsURL(String url);
}
