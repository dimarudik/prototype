package dev.bingo.provider;

import dev.bingo.HostProvider;
import dev.bingo.HostServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcURLProvider implements URLProvider {
    @Override
    public String getURL(String url) {
        if (!acceptsURL(url)) return url;

        String grpcTarget = extractGrpcTarget(url);

        ManagedChannel channel = ManagedChannelBuilder.forTarget(grpcTarget)
                .usePlaintext().build();
        try {
            HostServiceGrpc.HostServiceBlockingStub stub = HostServiceGrpc.newBlockingStub(channel);
            HostProvider.HostResponse response = stub.getHosts(HostProvider.HostRequest.newBuilder().setOriginalUrl(url).build());

            String realHost = response.getHosts(0);
            return url.replace("t-proxy:", "").replace(grpcTarget, realHost);
        } finally {
            channel.shutdown();
        }
    }

    private String extractGrpcTarget(String url) {
        return url.substring(url.indexOf("//") + 2, url.lastIndexOf("/"));
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:t-proxy:");
    }
}
