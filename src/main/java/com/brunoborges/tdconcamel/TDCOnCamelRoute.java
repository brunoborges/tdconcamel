package com.brunoborges.tdconcamel;

import java.io.InputStream;
import java.util.Properties;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.twitter.TwitterComponent;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

public class TDCOnCamelRoute extends RouteBuilder {

    public static final String UNIQUE_IMAGE_URL = "UNIQUE_IMAGE_URL";
    Properties configProperties = new Properties();

    @Override
    public void configure() throws Exception {
        setupTwitterComponent();

        final StatisticsProcessor statisticsProcessor = new StatisticsProcessor();
        statisticsProcessor.getCurrentStatistics().setKeywords(configProperties.getProperty("twitter.searchTerm"));

        from("twitter://streaming/sample?type=event")
                .to("seda:images")
                .routeId("twitterStreaming");

        from("seda:images")
                .process(new ImageExtractor())
                .process(statisticsProcessor)
                .filter(body().isInstanceOf(Tweet.class))
                .throttle(4)
                //.idempotentConsumer(header(UNIQUE_IMAGE_URL), MemoryIdempotentRepository.memoryIdempotentRepository(10000))
                .marshal().json(JsonLibrary.Jackson)
                .to("websocket:0.0.0.0:8080/tdconcamel/images?sendToAll=true&staticResources=classpath:web/.")
                .routeId("websocketImages");

        from("quartz:statistics?cron=* * * * * ?")
                .setBody().constant(statisticsProcessor.getCurrentStatistics())
                .marshal().json(JsonLibrary.Jackson)
                .to("websocket:0.0.0.0:8080/tdconcamel/statistics?sendToAll=true")
                .routeId("reportStatistics");

        from("websocket:0.0.0.0:8080/tdconcamel/statistics")
                .filter(body().isEqualTo("clear"))
                .bean(statisticsProcessor, "clear")
                .routeId("clearStatistics");

        from("websocket:0.0.0.0:8080/tdconcamel/images")
                .process(new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                String body = exchange.getIn().getBody(String.class);

                if (body.startsWith("search ") || body.equals("sample")) {
                    exchange.getContext().stopRoute("twitterStreaming");
                    exchange.getContext().removeRoute("twitterStreaming");
                }

                if (body.startsWith("search ")) {
                    final String query = body.substring(7); // search_
                    statisticsProcessor.getCurrentStatistics().setKeywords(query);
                    exchange.getContext().addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("twitter://streaming/filter?type=event&keywords=" + query)
                                    .to("seda:images")
                                    .routeId("twitterStreaming");
                        }
                    });
                } else if (body.equals("sample")) {
                    statisticsProcessor.getCurrentStatistics().setKeywords("sample");
                    exchange.getContext().addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("twitter://streaming/sample?type=event")
                                    .to("seda:images")
                                    .routeId("twitterStreaming");
                        }
                    });
                }
            }
        })
                .routeId("replaceStream");
    }

    private void setupTwitterComponent() {
        TwitterComponent tc = new TwitterComponent();
        getContext().addComponent("twitter", tc);

        String accessToken = System.getProperty("twitter.accessToken");
        String accessTokenSecret = System.getProperty("twitter.accessTokenSecret");
        String consumerKey = System.getProperty("twitter.consumerKey");
        String consumerSecret = System.getProperty("twitter.consumerSecret");

        try (InputStream is = TDCOnCamelRoute.class.getResourceAsStream("/app.properties")) {
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
