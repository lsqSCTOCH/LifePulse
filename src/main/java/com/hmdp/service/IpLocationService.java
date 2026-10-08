package com.hmdp.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.*;

@Service
public class IpLocationService {
    private final RestTemplate http;
    private final Map<String, Entry> cache = new LinkedHashMap<String, Entry>(128, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) { return size() > 512; }
    };
    private static final Map<String, String> CITY_NAMES = new HashMap<>();
    static {
        String[][] cities = {{"Hangzhou","杭州"},{"Beijing","北京"},{"Shanghai","上海"},{"Guangzhou","广州"},
                {"Shenzhen","深圳"},{"Chengdu","成都"},{"Chongqing","重庆"},{"Wuhan","武汉"},
                {"Nanjing","南京"},{"Suzhou","苏州"},{"Xi'an","西安"},{"Xian","西安"},{"Tianjin","天津"},
                {"Zhengzhou","郑州"},{"Changsha","长沙"},{"Qingdao","青岛"},{"Ningbo","宁波"},
                {"Fuzhou","福州"},{"Xiamen","厦门"},{"Dongguan","东莞"},{"Foshan","佛山"},
                {"Jinan","济南"},{"Hefei","合肥"},{"Kunming","昆明"},{"Shenyang","沈阳"}};
        for (String[] c : cities) CITY_NAMES.put(c[0].toLowerCase(Locale.ROOT), c[1]);
    }

    public IpLocationService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(4000);
        http = new RestTemplate(factory);
    }

    // Constructor for offline provider tests.
    public IpLocationService(RestTemplate http) { this.http = http; }

    public Map<String, Object> locate(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String ip = remote;
        // Only the local Nginx proxy is trusted; it overwrites X-Real-IP.
        if (isLoopback(remote)) {
            String forwarded = request.getHeader("X-Real-IP");
            if (literal(forwarded)) ip = forwarded;
        }
        if (!literal(ip)) return unavailable();
        // localhost development: provider sees this computer's network egress IP.
        // Private remote clients cannot be geolocated and must select a city themselves.
        boolean local = isLoopback(ip);
        if (!local && !publicIp(ip)) return unavailable();
        String key = local ? "local-egress" : ip;
        synchronized (cache) {
            Entry entry = cache.get(key);
            if (entry != null && entry.expires > System.currentTimeMillis()) return entry.value;
        }
        Map<String, Object> result;
        try {
            JsonNode data = http.getForObject("https://ipwho.is/" + (local ? "" : ip), JsonNode.class);
            String city = data == null ? "" : data.path("city").asText();
            if (data == null || !data.path("success").asBoolean() || city.isEmpty()) return unavailable();
            result = new LinkedHashMap<>();
            result.put("city", CITY_NAMES.getOrDefault(city.toLowerCase(Locale.ROOT), city));
            result.put("source", local ? "local-network-ip" : "client-ip");
            result.put("located", true);
            result.put("notice", "IP定位为城市级近似位置，可手动切换；代理或VPN可能影响结果。");
        } catch (RuntimeException ex) { result = unavailable(); }
        synchronized (cache) {
            cache.put(key, new Entry(result, System.currentTimeMillis() + (Boolean.TRUE.equals(result.get("located")) ? 1800000 : 60000)));
        }
        return result;
    }

    private Map<String, Object> unavailable() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", ""); result.put("source", "unavailable"); result.put("located", false);
        result.put("notice", "暂时无法通过IP定位，请手动选择城市。");
        return result;
    }

    private boolean literal(String ip) {
        if (ip == null || ip.length() > 45) return false;
        if (ip.contains(":")) return ip.matches("[0-9a-fA-F:]+") && address(ip) != null;
        if (!ip.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        for (String part : ip.split("\\.")) { try { if (Integer.parseInt(part) > 255) return false; } catch (NumberFormatException ex) { return false; } }
        return true;
    }
    private InetAddress address(String ip) { try { return InetAddress.getByName(ip); } catch (Exception ex) { return null; } }
    private boolean isLoopback(String ip) { return literal(ip) && address(ip).isLoopbackAddress(); }
    private boolean publicIp(String ip) {
        InetAddress a = address(ip);
        return a != null && !a.isAnyLocalAddress() && !a.isLoopbackAddress() && !a.isSiteLocalAddress()
                && !a.isLinkLocalAddress() && !a.isMulticastAddress()
                && !ip.startsWith("fc") && !ip.startsWith("fd");
    }
    private static class Entry {
        final Map<String, Object> value; final long expires;
        Entry(Map<String, Object> value, long expires) { this.value = value; this.expires = expires; }
    }
}
