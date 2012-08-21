package com.brunoborges.tdconcamel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import twitter4j.Status;

public class StatisticsProcessor implements Processor {

    private Statistics statistics = new Statistics();

    public Statistics getCurrentStatistics() {
        return statistics;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange.getIn().getBody() instanceof Status) {
            statistics.increaseTweetCount();
        } else if (exchange.getIn().getBody() instanceof Tweet) {
            statistics.increaseImageAndTweetCount();
        }

        exchange.getIn().setBody(statistics);
    }

    public void clear() {
        statistics.clear();
    }
}
