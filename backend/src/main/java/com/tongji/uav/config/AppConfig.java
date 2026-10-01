package com.tongji.uav.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;

/**
 * 基础 Bean 配置。
 */
@Configuration
public class AppConfig {

    /**
     * 调用算法服务的 RestTemplate：连接 5s / 读取 30s 超时（见 docs/api-contract.md），
     * 显式禁用系统代理，避免本机代理软件劫持对 127.0.0.1 的调用。
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(30_000);
        factory.setProxy(Proxy.NO_PROXY);
        return new RestTemplate(factory);
    }
}
