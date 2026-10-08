package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo-shops.enabled", havingValue = "true", matchIfMissing = false)
public class DemoShopSeeder implements CommandLineRunner {
    private final IShopService shops;
    private final IShopTypeService types;
    public DemoShopSeeder(IShopService shops, IShopTypeService types) { this.shops = shops; this.types = types; }

    @Override
    @Transactional
    public void run(String... args) {
        for (ShopType type : types.list()) {
            for (int i = 1; i <= 3; i++) {
                String name = "【演示】" + type.getName() + " · " + new String[]{"街角生活馆", "日光精选馆", "周末体验馆"}[i - 1];
                if (shops.query().eq("type_id", type.getId()).eq("name", name).count() > 0) continue;
                Shop shop = new Shop();
                shop.setName(name); shop.setTypeId(type.getId());
                shop.setImages("/imgs/demo/shop-" + type.getId() + ".svg");
                shop.setCity("演示"); shop.setArea("演示商圈"); shop.setAddress("演示地址 · 不对应真实经营场所");
                shop.setX(0.0); shop.setY(0.0); shop.setAvgPrice(0L);
                shop.setSold(0); shop.setComments(0); shop.setScore(0); shop.setOpenHours("演示数据，无实际营业时间");
                shops.save(shop);
            }
        }
    }
}
