package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.utils.UserHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService  voucherOrderService;

    @GetMapping("/mine")
    public Result myOrders() {
        return Result.ok(voucherOrderService.query()
                .eq("user_id", UserHolder.getUser().getId())
                .orderByDesc("create_time").last("LIMIT 10").list()
                .stream().map(this::orderView).collect(Collectors.toList()));
    }

    @GetMapping("/mine/{id}")
    public Result myOrder(@PathVariable("id") Long id) {
        VoucherOrder order = voucherOrderService.query()
                .eq("id", id).eq("user_id", UserHolder.getUser().getId()).one();
        return order == null ? Result.fail("订单不存在或无权访问") : Result.ok(orderView(order));
    }

    private Map<String, Object> orderView(VoucherOrder order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", String.valueOf(order.getId()));
        view.put("voucherId", String.valueOf(order.getVoucherId()));
        view.put("status", order.getStatus());
        String[] labels = {"未知", "未支付", "已支付", "已核销", "已取消", "退款中", "已退款"};
        Integer status = order.getStatus();
        view.put("statusLabel", status != null && status >= 1 && status < labels.length ? labels[status] : "未知");
        view.put("createTime", order.getCreateTime());
        return view;
    }
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
