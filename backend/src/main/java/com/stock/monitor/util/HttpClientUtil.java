package com.stock.monitor.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.Map;

/**
 * HTTP客户端工具类 - API请求封装
 */
@Component
public class HttpClientUtil {

    private static final Logger log = LoggerFactory.getLogger(HttpClientUtil.class);

    @Resource
    private RestTemplate restTemplate;

    /**
     * GET请求
     */
    public String doGet(String url) {
        return doGet(url, null);
    }

    /**
     * GET请求（带请求头）
     */
    public String doGet(String url, Map<String, String> headers) {
        try {
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            httpHeaders.set("Accept", "application/json, text/plain, */*");
            httpHeaders.set("Accept-Language", "zh-CN,zh;q=0.9");
            if (headers != null) {
                headers.forEach(httpHeaders::set);
            }

            HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
            log.warn("HTTP请求返回非200状态码: {} URL: {}", response.getStatusCode(), url);
        } catch (RestClientException e) {
            log.error("HTTP GET请求失败: {} - {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * POST请求
     */
    public String doPost(String url, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            String jsonBody = body instanceof String ? (String) body : JSON.toJSONString(body);
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            }
        } catch (RestClientException e) {
            log.error("HTTP POST请求失败: {} - {}", url, e.getMessage());
        }
        return null;
    }

    /**
     * 带重试的GET请求
     */
    public String doGetWithRetry(String url, int maxRetry, long retryIntervalMs) {
        for (int i = 0; i < maxRetry; i++) {
            String result = doGet(url);
            if (result != null) {
                return result;
            }
            log.info("请求失败，第{}次重试... URL: {}", i + 1, url);
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        log.error("请求重试{}次后仍失败: {}", maxRetry, url);
        return null;
    }

    /**
     * 解析JSON响应
     */
    public JSONObject parseJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return null;
        }
        try {
            return JSON.parseObject(response);
        } catch (Exception e) {
            log.error("JSON解析失败: {}", e.getMessage());
            return null;
        }
    }
}
