package io.quarkiverse.tekton.deployment.devservices;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * A build item that represents the information required to connect to an Tekton dev service.
 */
public final class TektonDevServiceInfoBuildItem extends SimpleBuildItem {

    private final String hostName;
    private final int hostPort;

    public TektonDevServiceInfoBuildItem(String hostName, int hostPort) {
        this.hostName = hostName;
        this.hostPort = hostPort;
    }

    public int hostPort() {
        return hostPort;
    }

    public String host() {
        return hostName;
    }
}
