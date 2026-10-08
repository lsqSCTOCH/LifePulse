package com.hmdp.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.SystemConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/shop")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {
        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param shop 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Shop shop) {
        // 写入数据库
        shopService.save(shop);
        return Result.ok();
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@RequestBody Shop shop) {
        // 写入数据库
        return shopService.update(shop);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x",required = false) Double x,
            @RequestParam(value = "y",required = false) Double y,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "sortBy", defaultValue = "") String sortBy
    ) {
        if (current < 1 || typeId < 1) return Result.fail("分页或分类参数无效");
        if (StrUtil.isNotBlank(city) || x == null || y == null) {
            LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>().eq(Shop::getTypeId, typeId);
            filterCity(query, city);
            if ("comments".equals(sortBy)) query.orderByDesc(Shop::getComments);
            if ("score".equals(sortBy)) query.orderByDesc(Shop::getScore);
            query.orderByAsc(Shop::getId);
            Page<Shop> page = shopService.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), query);
            return Result.ok(page.getRecords());
        }
        return shopService.queryShopByType(typeId,current,x,y);
    }

    /** 固定展示清水河校区周边资料，与用户的 IP 城市分开。 */
    @GetMapping("/of/campus")
    public Result queryCampusShops(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "sortBy", defaultValue = "") String sortBy) {
        if (current < 1 || typeId < 1) return Result.fail("分页或分类参数无效");
        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getTypeId, typeId).eq(Shop::getCity, "成都")
                .eq(Shop::getCampus, "清水河").isNotNull(Shop::getCatalogKey);
        if ("comments".equals(sortBy)) query.orderByDesc(Shop::getComments);
        if ("score".equals(sortBy)) query.orderByDesc(Shop::getScore);
        query.orderByAsc(Shop::getId);
        return Result.ok(shopService.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), query).getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "city", required = false) String city
    ) {
        // 根据类型分页查询
        if (current < 1) return Result.fail("分页参数无效");
        LambdaQueryWrapper<Shop> query = new LambdaQueryWrapper<Shop>()
                .like(StrUtil.isNotBlank(name), Shop::getName, name);
        filterCity(query, city);
        query.orderByAsc(Shop::getId);
        Page<Shop> page = shopService.page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), query);
        // 返回数据
        return Result.ok(page.getRecords());
    }

    private void filterCity(LambdaQueryWrapper<Shop> query, String city) {
        // 未替换的演示槽位保留记录以兼容已有笔记引用，但不再作为商户展示。
        query.notLike(Shop::getName, "【演示】");
        if (StrUtil.isNotBlank(city)) {
            query.and(q -> q.eq(Shop::getCity, city).or().eq(Shop::getCity, "演示"));
        }
    }
}
