package com.brunoborges.tdconcamel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;

public class TDCOnCamelRoute extends RouteBuilder {

    public static final String UNIQUE_IMAGE_URL = "UNIQUE_IMAGE_URL";

    @Override
    public void configure() throws Exception {
        PropertiesComponent propertiesComponent = new PropertiesComponent();
        propertiesComponent.setLocation("classpath:app.properties");
        getContext().addComponent("properties", propertiesComponent);

        ImageExtractor imageExtractor = new ImageExtractor();
        final StatisticsProcessor statisticsProcessor = new StatisticsProcessor();

        //from("twitter://streaming/filter?type=event&keywords={{twitter.searchTerm}}&accessToken={{twitter.accessToken}}&accessTokenSecret={{twitter.accessTokenSecret}}&consumerKey={{twitter.consumerKey}}&consumerSecret={{twitter.consumerSecret}}")
        from("twitter://streaming/sample?type=event&accessToken={{twitter.accessToken}}&accessTokenSecret={{twitter.accessTokenSecret}}&consumerKey={{twitter.consumerKey}}&consumerSecret={{twitter.consumerSecret}}")
                .to("log:tweetStream?level=INFO&groupInterval=60000&groupDelay=60000&groupActiveOnly=false")
                .process(imageExtractor)
                .wireTap("direct:statistics")
                .filter(body().isInstanceOf(Tweet.class))
                .idempotentConsumer(header(UNIQUE_IMAGE_URL), MemoryIdempotentRepository.memoryIdempotentRepository(10000))
                .to("log:imageStream?level=INFO&groupInterval=60000&groupDelay=60000&groupActiveOnly=false")
                .marshal().json(JsonLibrary.Jackson)
                .to("websocket:0.0.0.0/tdconcamel/images?sendToAll=true&staticResources=classpath:web/.")
                .routeId("twitterStreaming");

        from("direct:statistics")
                .process(statisticsProcessor)
                .routeId("statistics");

        from("quartz:statistics?cron=* * * * * ?")
                .setBody().constant(statisticsProcessor.getCurrentStatistics())
                .marshal().json(JsonLibrary.Jackson)
                .to("websocket:0.0.0.0/tdconcamel/statistics?sendToAll=true")
                .routeId("reportStatistics");

        from("websocket:0.0.0.0/tdconcamel/statistics")
                .filter(body().isEqualTo("clear"))
                .bean(statisticsProcessor, "clear")
                .routeId("clearStatistics");
    }
}
