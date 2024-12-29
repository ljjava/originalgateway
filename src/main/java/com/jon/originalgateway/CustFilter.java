package com.jon.originalgateway;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR;
@Component
public class CustFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // ServerHttpRequest request = exchange.getRequest();
        System.out.println("进入全局");
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        long startTime = System.currentTimeMillis();
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                @SuppressWarnings("unchecked")
                Flux<DataBuffer> fluxBody = (Flux<DataBuffer>) body;
                return super.writeWith(fluxBody.map(dataBuffer -> {
                    // 读取响应数据
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);
                    DataBufferUtils.release(dataBuffer);
                    content = mergeByteArrays(content);
                    // 转换响应内容为字符串
                    String responseBody = new String(content, StandardCharsets.UTF_8);
                    // 记录响应信息
                    long endTime = System.currentTimeMillis();
                    logResponse(path,getStatusCode().value(), responseBody, endTime - startTime);
                    ServerHttpResponse response = getDelegate();
                    response.getHeaders().setContentLength(content.length);
                    // 重新包装响应内容
                    return  response.bufferFactory().wrap(content);
                }));
                // return super.writeWith(body);
            }
        };
        ServerWebExchange decoratedExchange = exchange.mutate().response(decoratedResponse).build();
        return chain.filter(decoratedExchange);    
    }
    private void logResponse(String path, int statusCode, String responseBody, long processingTime) {
        try {
            System.out.println("=== Response Log ===");
            System.out.println("Path: " + path);
            System.out.println("Status Code: " + statusCode);
            System.out.println("Processing Time: " + processingTime + "ms");
            System.out.println("Response Body: " + responseBody);
            System.out.println("==================");
            
        } catch (Exception e) {
            System.err.println("Error logging response: " + e.getMessage());
        }
    }
    private byte[] mergeByteArrays(byte[] arg1) {
        byte[] arg2 = "  hahahahahah".getBytes();
        byte[] result = new byte[arg1.length + arg2.length];
        System.arraycopy(arg1, 0, result, 0, arg1.length);
        System.arraycopy(arg2, 0, result, arg1.length, arg2.length);
        return result;
    }


    @Override
    public int getOrder() {
        return -1; // 设置过滤器的优先级
    }
}