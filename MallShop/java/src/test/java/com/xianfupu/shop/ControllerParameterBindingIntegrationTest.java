package com.xianfupu.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ControllerParameterBindingIntegrationTest {
    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void adminOrdersAcceptDefaultAndExplicitQueryParameters() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user("1").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
        mvc.perform(get("/api/admin/orders").param("status", "WAIT_PAY")
                        .param("page", "0").param("size", "1").with(user("1").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test
    void catalogAcceptsMissingAndExplicitOptionalQueryParameters() throws Exception {
        mvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/catalog/products").param("keyword", "不存在的回归测试商品")
                        .param("sort", "default").param("specialOnly", "false")
                        .param("memberOnly", "false").param("page", "0").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void productAndFilePathsBindWithoutCompilerParameterNames() throws Exception {
        mvc.perform(get("/api/catalog/products/p1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/files/missing-regression-image.png"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }
}
