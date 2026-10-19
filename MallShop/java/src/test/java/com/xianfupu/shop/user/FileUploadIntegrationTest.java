package com.xianfupu.shop.user;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class FileUploadIntegrationTest {
    @TempDir
    static Path uploads;

    @DynamicPropertySource
    static void uploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.upload-dir", () -> uploads.toString());
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void storesAdminImageAndServesItPublicly() throws Exception {
        byte[] image = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        var result = mvc.perform(multipart("/api/admin/uploads/images")
                        .file(new MockMultipartFile("file", "product.png", "image/png", image))
                        .with(user("1").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.url");
        assertThat(url).matches("/api/files/[a-f0-9]{32}\\.png");
        assertThat(Files.readAllBytes(uploads.resolve(url.substring("/api/files/".length())))).isEqualTo(image);
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(image));
    }

    @Test
    void restrictsAdminUploadsToAdministrators() throws Exception {
        var file = new MockMultipartFile("file", "product.png", "image/png", new byte[]{1});
        mvc.perform(multipart("/api/admin/uploads/images").file(file))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/uploads/images").file(file).with(user("2").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsEmptyNonImageAndOversizedFiles() throws Exception {
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "note.txt", "text/plain", new byte[]{1}),
                new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1])
        }) {
            mvc.perform(multipart("/api/admin/uploads/images").file(file).with(user("1").roles("ADMIN")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
        }
    }
}
