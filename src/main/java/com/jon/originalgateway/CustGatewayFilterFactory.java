package com.jon.originalgateway;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.HttpStatusCode;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.*;


class ResponseBodyDecorator extends ServerHttpResponseDecorator {
    private final StringBuilder bodyBuilder = new StringBuilder();
    public ResponseBodyDecorator(ServerHttpResponse delegate) {
        super(delegate);
    }
    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        if (body instanceof Flux) {
            Flux<DataBuffer> fluxBody = (Flux<DataBuffer>) body;
            return super.writeWith(fluxBody.map(dataBuffer -> {
                // 读取数据
                byte[] content = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(content);
                
                // 释放 DataBuffer
                DataBufferUtils.release(dataBuffer);
                
                // 将内容追加到 StringBuilder
                bodyBuilder.append(new String(content, StandardCharsets.UTF_8));
                
                // 重新创建 DataBuffer
                return getDelegate().bufferFactory().wrap(content);
            }));
        }
        return super.writeWith(body);
    }
    public String getBodyContent() {
        return bodyBuilder.toString();
    }
    
}

@Component
public class CustGatewayFilterFactory extends AbstractGatewayFilterFactory<CustGatewayFilterFactory.Config>
        implements Ordered {

    // 定义服务器列表
    private static final List<String> SERVER_URLS = Arrays.asList(
            "http://localhost:8081",
            "http://localhost:8082");

    // 使用 AtomicInteger 来确保线程安全的计数
    private final AtomicInteger counter = new AtomicInteger(0);

    public CustGatewayFilterFactory() {
        super(Config.class);
    }

    // 获取下一个服务器地址
    private String getNextServer() {
        // 获取当前计数并自增，使用取模来循环计数
        int current = counter.getAndIncrement() % SERVER_URLS.size();
        return SERVER_URLS.get(current);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest originalRequest = exchange.getRequest();
            String originalPath = originalRequest.getURI().getPath();
            String originalQuery = originalRequest.getURI().getQuery();
            // 获取下一个目标服务器
            String targetServer = getNextServer();
            // 构建新的路径
            String newPath;
            if (originalPath.startsWith("/loadbalance/")) {
                newPath = "/" + originalPath.substring("/loadbalance/".length());
            } else {
                newPath = originalPath;
            }
            // 构建完整的URI
            StringBuilder newUriBuilder = new StringBuilder(targetServer);
            newUriBuilder.append(newPath);
            if (originalQuery != null && !originalQuery.isEmpty()) {
                newUriBuilder.append("?").append(originalQuery);
            }
            URI newUri = URI.create(newUriBuilder.toString());
            // 创建新的Route对象
            Route newRoute = Route.async().id("modified-route").uri(newUri).order(0).predicate(serverWebExchange -> true).build();
            // 创建新的请求
            ServerHttpRequest newRequest = originalRequest.mutate().path(newPath).build();
            // 创建新的exchange
            ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
            // 设置新的路由和请求URL
            newExchange.getAttributes().put(GATEWAY_ROUTE_ATTR, newRoute);
            // newExchange.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, newUri);

            System.out.println("CustGatewayFilter轮询转发请求到: " + newUri);
            URI finalRequestUri = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
            if (finalRequestUri != null) {
                System.out.println("最终请求 URI: " + finalRequestUri);
            }else {
                System.out.println("最终请求 URI 为空");
            }
            
            return chain.filter(newExchange).then(Mono.fromRunnable(() -> {
                int statusCode = newExchange.getResponse().getStatusCode().value();
                System.out.println("响应状态码: " + statusCode);         
            }));
        };
    }

    public static class Config {
        // 配置类，如果需要可以添加配置属性
    }

    @Override
    public int getOrder() {
        // return Ordered.HIGHEST_PRECEDENCE;
        return Ordered.LOWEST_PRECEDENCE;
    }
}