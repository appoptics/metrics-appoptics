package com.appoptics.metrics.reporter;

import com.appoptics.metrics.client.AppopticsClient;
import com.appoptics.metrics.client.AppopticsClientBuilder;

public class DefaultAppopticsClientFactory implements IAppopticsClientFactory {
    public AppopticsClient build(ReporterAttributes atts) {
        AppopticsClientBuilder builder = AppopticsClient.builder(atts.token)
                .setURI(atts.url)
                .setAgentIdentifier(Agent.AGENT_IDENTIFIER);
        if (atts.readTimeout != null) {
            builder.setReadTimeout(atts.readTimeout);
        }
        if (atts.connectTimeout != null) {
            builder.setConnectTimeout(atts.connectTimeout);
        }
        if (atts.poster != null) {
            builder.setPoster(atts.poster);
        }
        return builder.build();
    }
}
