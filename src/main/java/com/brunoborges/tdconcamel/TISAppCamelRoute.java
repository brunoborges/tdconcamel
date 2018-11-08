package com.brunoborges.tdconcamel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twitter.TwitterComponent;
import org.apache.camel.component.twitter.streaming.TwitterStreamingComponent;
import org.apache.camel.component.websocket.WebsocketComponent;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.ShutdownStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TISAppCamelRoute extends RouteBuilder {

    public static final String UNIQUE_IMAGE_URL = "UNIQUE_IMAGE_URL";
    Properties configProperties = new Properties();

    private static final Logger LOGGER = LoggerFactory.getLogger(TISAppCamelRoute.class);

    @Override
    public void configure() throws Exception {

        // Shutdown fast (3 seconds at most)
        ShutdownStrategy shutdownStrategy = getContext().getShutdownStrategy();
        shutdownStrategy.setShutdownNowOnTimeout(true);
        shutdownStrategy.setTimeout(3);
        shutdownStrategy.setTimeUnit(TimeUnit.SECONDS);

        // setup Twitter component
        TwitterStreamingComponent tc =
                getContext().getComponent("twitter-streaming", TwitterStreamingComponent.class);
        setupTwitterComponent(tc);

        WebsocketComponent wsc = getContext().getComponent("websocket", WebsocketComponent.class);
        wsc.setMaxThreads(20);

        String initialSearchTerm = configProperties.getProperty("twitter.searchTerm");
        final StatisticsProcessor statisticsProcessor = new StatisticsProcessor();
        statisticsProcessor.getCurrentStatistics().setKeywords(initialSearchTerm);

        from("twitter-streaming://filter?type=event&keywords=" + initialSearchTerm)
                .to("seda:images").routeId("twitterStreaming");

        from("seda:images").process(new ImageExtractor()).process(statisticsProcessor)
                .filter(body().isInstanceOf(Tweet.class)).throttle(4)
                .idempotentConsumer(header(UNIQUE_IMAGE_URL),
                        MemoryIdempotentRepository.memoryIdempotentRepository(10000))
                .marshal().json(JsonLibrary.Gson)
                .process(exchange -> exchange.getIn()
                        .setBody(new String((byte[]) exchange.getIn().getBody())))
                .to("websocket:0.0.0.0:8080/images?sendToAll=true&staticResources=classpath:web/.")
                .routeId("websocketImages");

        from("quartz:statistics?cron=* * * * * ?").setBody()
                .constant(statisticsProcessor.getCurrentStatistics()).marshal()
                .json(JsonLibrary.Gson)
                .process(exchange -> exchange.getIn()
                        .setBody(new String((byte[]) exchange.getIn().getBody())))
                .process(exchange -> LOGGER.info("Tweet received: " + exchange.getIn().getBody()))
                .to("websocket:0.0.0.0:8080/statistics?sendToAll=true").routeId("reportStatistics");

        from("websocket:0.0.0.0:8080/statistics").filter(body().isEqualTo("clear"))
                .bean(statisticsProcessor, "clear").routeId("clearStatistics");

        from("websocket:0.0.0.0:8080/images").process(exchange -> {
            String body = exchange.getIn().getBody(String.class);

            if (body.startsWith("search ") || body.equals("sample")) {
                exchange.getContext().stopRoute("twitterStreaming", 3, TimeUnit.SECONDS);
                exchange.getContext().removeRoute("twitterStreaming");
            }

            if (body.startsWith("search ")) {
                final String query = body.substring(7); // search_
                statisticsProcessor.getCurrentStatistics().setKeywords(query);
                exchange.getContext().addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("twitter-streaming://filter?type=event&keywords=" + query)
                                .to("seda:images").routeId("twitterStreaming");
                    }
                });
            } else if (body.equals("sample")) {
                statisticsProcessor.getCurrentStatistics().setKeywords("sample");
                exchange.getContext().addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("twitter-streaming://sample?type=event").to("seda:images")
                                .routeId("twitterStreaming");
                    }
                });
            }
        }).routeId("replaceStream");
    }

    private void setupTwitterComponent(TwitterStreamingComponent tc) {
        String accessToken = System.getProperty("twitter.accessToken");
        String accessTokenSecret = System.getProperty("twitter.accessTokenSecret");
        String consumerKey = System.getProperty("twitter.consumerKey");
        String consumerSecret = System.getProperty("twitter.consumerSecret");

        try (InputStream is = this.getClass().getResourceAsStream("/app.properties")) {
            configProperties.load(is);

            if (accessToken == null) {
                accessToken = configProperties.getProperty("twitter.accessToken");
            }

            if (accessTokenSecret == null) {
                accessTokenSecret = configProperties.getProperty("twitter.accessTokenSecret");
            }

            if (consumerKey == null) {
                consumerKey = configProperties.getProperty("twitter.consumerKey");
            }

            if (consumerSecret == null) {
                consumerSecret = configProperties.getProperty("twitter.consumerSecret");
            }
        } catch (Exception e) {
        }

        tc.setAccessToken(accessToken);
        tc.setAccessTokenSecret(accessTokenSecret);
        tc.setConsumerKey(consumerKey);
        tc.setConsumerSecret(consumerSecret);
    }
}
