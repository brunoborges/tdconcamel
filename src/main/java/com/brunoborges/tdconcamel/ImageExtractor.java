package com.brunoborges.tdconcamel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import twitter4j.MediaEntity;
import twitter4j.Status;

public class ImageExtractor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Status status = exchange.getIn().getBody(Status.class);

        MediaEntity[] mediaEntities = status.getMediaEntities();
        if (mediaEntities != null && mediaEntities.length > 0) {
            MediaEntity mediaEntity = mediaEntities[0]; // only the first image

            exchange.getIn().setBody(new Tweet()
                    .withName(status.getUser().getScreenName())
                    .withText(status.getText())
                    .withUrl(mediaEntity.getMediaURL().toString()));

            exchange.getIn().setHeader(TDCOnCamelRoute.UNIQUE_IMAGE_URL, mediaEntity.getMediaURL().toString());
        }
    }
}
