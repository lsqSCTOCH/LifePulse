package com.hmdp.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VoucherOrderQueryTest {
    @AfterEach
    void clearUser() { UserHolder.removeUser(); }

    @Test
    @SuppressWarnings("unchecked")
    void queriesBothOrderIdAndAuthenticatedUserAndKeepsIdPrecision() {
        UserDTO user = new UserDTO(); user.setId(42L); UserHolder.saveUser(user);
        IVoucherOrderService service = mock(IVoucherOrderService.class);
        BaseMapper<VoucherOrder> mapper = mock(BaseMapper.class);
        when(service.query()).thenReturn(new QueryChainWrapper<>(mapper));
        long orderId = 9007199254740993L;
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            QueryWrapper<VoucherOrder> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("user_id", "id");
            assertThat(query.getParamNameValuePairs().values()).contains(42L, orderId);
            VoucherOrder order = new VoucherOrder();
            order.setId(orderId); order.setUserId(42L); order.setVoucherId(1L); order.setStatus(2);
            return order;
        });
        VoucherOrderController controller = new VoucherOrderController();
        ReflectionTestUtils.setField(controller, "voucherOrderService", service);
        com.hmdp.dto.Result result = controller.myOrder(orderId);
        assertThat(result.getSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data.get("id")).isEqualTo("9007199254740993");
        assertThat(data.get("statusLabel")).isEqualTo("已支付");
        assertThat(data).doesNotContainKey("userId");
        verify(mapper).selectOne(any());
    }

    @Test
    void rejectsUnauthenticatedOrderQueryBeforeCallingService() throws Exception {
        IVoucherOrderService service = mock(IVoucherOrderService.class);
        VoucherOrderController controller = new VoucherOrderController();
        ReflectionTestUtils.setField(controller, "voucherOrderService", service);
        org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).addInterceptors(new LoginInterceptor()).build();
        mvc.perform(get("/voucher-order/mine/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
