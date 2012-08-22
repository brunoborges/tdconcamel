package com.brunoborges.tdconcamel;

import java.io.IOException;
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
                .to("direct:images")
                .routeId("twitterStreaming");

        from("direct:images")
                .process(new ImageExtractor())
                .process(statisticsProcessor)
                .filter(body().isInstanceOf(Tweet.class))
                .idempotentConsumer(header(UNIQUE_IMAGE_URL), MemoryIdempotentRepository.memoryIdempotentRepository(10000))
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
                exchange.getContext().stopRoute("twitterStreaming");
                exchange.getContext().removeRoute("twitterStreaming");

                String body = exchange.getIn().getBody(String.class);

                if (body.startsWith("search ")) {
                    final String query = body.substring(7); // search_
                    statisticsProcessor.getCurrentStatistics().setKeywords(query);
                    exchange.getContext().addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("twitter://streaming/filter?type=event&keywords=" + query)
                                    .to("direct:images")
                                    .routeId("twitterStreaming");
                        }
                    });
                } else if (body.equals("sample")) {
                    statisticsProcessor.getCurrentStatistics().setKeywords("sample");
                    exchange.getContext().addRoutes(new RouteBuilder() {
                        @Override
                        public void configure() throws Exception {
                            from("twitter://streaming/sample?type=event")
                                    .to("direct:images")
                                    .routeId("twitterStreaming");
                        }
                    });
                }
            }
        })
                .routeId("replaceStream");
    }

    private void setupTwitterComponent() throws IOException {
        TwitterComponent tc = new TwitterComponent();
        getContext().addComponent("twitter", tc);

        configProperties.load(TDCOnCamelRoute.class.getResourceAsStream("/app.properties"));
        tc.setAccessToken(configProperties.getProperty("twitter.accessToken"));
        tc.setAccessTokenSecret(configProperties.getProperty("twitter.accessTokenSecret"));
        tc.setConsumerKey(configProperties.getProperty("twitter.consumerKey"));
        tc.setConsumerSecret(configProperties.getProperty("twitter.consumerSecret"));
    }
}
