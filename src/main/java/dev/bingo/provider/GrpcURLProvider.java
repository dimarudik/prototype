package dev.bingo.provider;

import dev.bingo.provider.grpc.HostProvider;
import dev.bingo.provider.grpc.HostServiceGrpc;
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

            HostProvider.HostRequest request = HostProvider.HostRequest.newBuilder()
                    .setOriginalUrl(url)
                    .build();

            HostProvider.HostResponse response = stub.getHosts(request);

            HostProvider.HostInfo selectedHost = response.getHostsList().stream()
                    .filter(h -> h.getType() == HostProvider.HostType.MASTER)
                    .findFirst()
                    .orElseGet(() -> {
                        return response.getHosts(0);
                    });

            String realHost = selectedHost.getHost();
            return url.replace("t-proxy:", "").replace(grpcTarget, realHost);

        } catch (Exception e) {
            throw new RuntimeException("Host discovery failed", e);
        } finally {
            channel.shutdown();
        }
    }

    private String extractGrpcTarget(String url) {
        // Извлекаем host:port из jdbc:t-proxy:postgresql://host:port/db
        try {
            int start = url.indexOf("//") + 2;
            int end = url.indexOf("/", start);
            return url.substring(start, end);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JDBC URL format for gRPC discovery: " + url);
        }
    }
    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith("jdbc:t-proxy:");
    }
}
