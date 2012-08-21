package com.brunoborges.tdconcamel;

public class Tweet {
    private String name;
    private String text;
    private String url;

    private long tweetCount;
    private long imageCount;

    public Tweet withName(String name) {
        this.name = name;
        return this;
    }

    public Tweet withText(String text) {
        this.text = text;
        return this;
    }

    public Tweet withImageUrl(String url) {
        this.url = url;
        return this;
    }
    
    public Tweet withCount(long tweetCount) {
        this.tweetCount = tweetCount;
        return this;
    }

    public Tweet withImageCount(long imageCount) {
        this.imageCount = imageCount;
        return this;
    }

    public long getImageCount() {
        return imageCount;
    }

    public String getName() {
        return name;
    }

    public String getText() {
        return text;
    }

    public long getTweetCount() {
        return tweetCount;
    }

    public String getUrl() {
        return url;
    }
}
