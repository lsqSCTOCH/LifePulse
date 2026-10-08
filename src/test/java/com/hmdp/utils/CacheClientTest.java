package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static com.hmdp.utils.RedisConstants.*;

class CacheClientTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private CacheClient cache;
    private Function<Long, Shop> database;
    private final String key = CACHE_SHOP_KEY + "1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        database = mock(Function.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new CacheClient(redis);
    }

    private Shop query() {
        return cache.queryWithLogicalExpire(CACHE_SHOP_KEY, 1L, Shop.class, database, 30L, TimeUnit.MINUTES);
    }

    private Shop shop() {
        Shop shop = new Shop(); shop.setId(1L); shop.setName("103茶餐厅"); return shop;
    }

    @Test
    void coldCacheLoadsExistingShopAndWritesLogicalExpiry() {
        Shop expected = shop(); when(database.apply(1L)).thenReturn(expected);
        assertThat(query()).isSameAs(expected);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(key), json.capture());
        RedisData stored = JSONUtil.toBean(json.getValue(), RedisData.class);
        assertThat(stored.getExpireTime()).isAfter(LocalDateTime.now());
        assertThat(JSONUtil.toBean(JSONUtil.toJsonStr(stored.getData()), Shop.class).getId()).isEqualTo(1L);
    }

    @Test
    void missingShopWritesShortLivedNegativeCache() {
        assertThat(query()).isNull();
        verify(database).apply(1L);
        verify(values).set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
    }

    @Test
    void negativeCacheDoesNotQueryDatabase() {
        when(values.get(key)).thenReturn("");
        assertThat(query()).isNull();
        verifyNoInteractions(database);
    }

    @Test
    void freshLogicalCacheDoesNotQueryDatabase() {
        RedisData stored = new RedisData(); stored.setData(shop());
        stored.setExpireTime(LocalDateTime.now().plusMinutes(30));
        when(values.get(key)).thenReturn(JSONUtil.toJsonStr(stored));
        assertThat(query().getName()).isEqualTo("103茶餐厅");
        verifyNoInteractions(database);
    }

    @Test
    void legacyEntityCacheIsConvertedToLogicalFormat() {
        when(values.get(key)).thenReturn(JSONUtil.toJsonStr(shop()));
        assertThat(query().getId()).isEqualTo(1L);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq(key), json.capture());
        assertThat(JSONUtil.parseObj(json.getValue()).containsKey("expireTime")).isTrue();
        verifyNoInteractions(database);
    }
}
