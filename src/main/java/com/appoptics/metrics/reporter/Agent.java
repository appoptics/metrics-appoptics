package com.appoptics.metrics.reporter;

import com.codahale.metrics.Metric;
import com.appoptics.metrics.client.Versions;

public class Agent {
    /**
     * a string used to identify the library
     */
    public static final String AGENT_IDENTIFIER;

    static {
        String metricsCoreVersion = Versions.getVersion(
                "META-INF/maven/io.dropwizard.metrics/metrics-core/pom.properties",
                Metric.class);
        String metricsLibratoVersion = Versions.getVersion(
                "META-INF/maven/com.appoptics.metrics/metrics-appoptics/pom.properties",
                AppopticsReporter.class);
        AGENT_IDENTIFIER = String.format(
                "metrics-appoptics/%s metrics/%s",
                metricsLibratoVersion,
                metricsCoreVersion);
    }
}
