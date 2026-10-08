package com.hmdp.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 仅升级本项目的演示记录；不将原始杭州商户或其优惠券改成成都商户。 */
@Component
@Order(-90)
@ConditionalOnProperty(name = "app.campus-shops.enabled", havingValue = "true", matchIfMissing = true)
public class CampusShopSeeder implements CommandLineRunner {
    private final IShopService shops;
    private final IShopTypeService types;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;

    public CampusShopSeeder(IShopService shops, IShopTypeService types, ObjectMapper mapper, StringRedisTemplate redis) {
        this.shops = shops; this.types = types; this.mapper = mapper; this.redis = redis;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        List<Shop> catalog;
        try (InputStream input = new ClassPathResource("campus-shops.json").getInputStream()) {
            catalog = mapper.readValue(input, new TypeReference<List<Shop>>() {});
        }
        Map<Long, String> names = types.list().stream().collect(Collectors.toMap(ShopType::getId, ShopType::getName));
        String[] suffixes = {"街角生活馆", "日光精选馆", "周末体验馆"};
        for (Shop shop : catalog) {
            Shop existing = shops.query().eq("catalog_key", shop.getCatalogKey()).one();
            if (existing == null) {
                int index = Integer.parseInt(shop.getCatalogKey().substring(shop.getCatalogKey().lastIndexOf('-') + 1)) - 1;
                String demoName = "【演示】" + names.get(shop.getTypeId()) + " · " + suffixes[index];
                existing = shops.query().eq("type_id", shop.getTypeId()).eq("name", demoName).one();
            }
            if (existing == null) {
                shops.save(shop);
            } else {
                shop.setId(existing.getId());
                shops.updateById(shop);
                redis.delete("cache:shop:" + shop.getId());
            }
        }
    }
}
