package com.hmdp.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShopCityQueryTest {
    @Test
    @SuppressWarnings("unchecked")
    void campusCatalogCannotIncludeOtherCitiesOrDemoRecords() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Shop.class);
        IShopService service = mock(IShopService.class);
        doAnswer(invocation -> {
            LambdaQueryWrapper<Shop> query = invocation.getArgument(1);
            assertThat(query.getSqlSegment()).contains("catalog_key IS NOT NULL", "campus").doesNotContain(" OR ");
            assertThat(query.getParamNameValuePairs().values()).contains("成都", "清水河", 1).doesNotContain("演示", "杭州");
            return new Page<Shop>(1, 10).setRecords(Collections.emptyList());
        }).when(service).page(any(Page.class), any(Wrapper.class));
        ShopController controller = new ShopController(); controller.shopService = service;
        assertThat(controller.queryCampusShops(1, 1, "score").getSuccess()).isTrue();
        assertThat(controller.queryCampusShops(1, 0, "score").getSuccess()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersSelectedCityOrDemoAndSupportsScoreSortWithoutFixedCoordinates() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Shop.class);
        IShopService service = mock(IShopService.class);
        doAnswer(invocation -> {
            LambdaQueryWrapper<Shop> query = invocation.getArgument(1);
            assertThat(query.getSqlSegment()).contains("city", "OR", "score DESC");
            assertThat(query.getParamNameValuePairs().values()).contains("深圳", "演示", 1);
            assertThat(query.getParamNameValuePairs().values()).doesNotContain("杭州");
            return new Page<Shop>(1, 10).setRecords(Collections.emptyList());
        }).when(service).page(any(Page.class), any(Wrapper.class));
        ShopController controller = new ShopController();controller.shopService=service;
        assertThat(controller.queryShopByType(1,1,null,null,"深圳","score").getSuccess()).isTrue();
        verify(service, never()).queryShopByType(anyInt(), anyInt(), any(), any());
    }
}
