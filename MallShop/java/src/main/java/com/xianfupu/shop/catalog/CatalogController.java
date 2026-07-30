package com.xianfupu.shop.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    List<CatalogService.CategoryView> categories() {
        return catalogService.categories();
    }

    @GetMapping("/products")
    CatalogService.ProductPage products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "default") String sort,
            @RequestParam(defaultValue = "false") boolean specialOnly,
            @RequestParam(defaultValue = "false") boolean memberOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return catalogService.products(category, keyword, sort, specialOnly, memberOnly, page, size);
    }

    @GetMapping("/products/{productCode}")
    CatalogService.ProductDetail product(@PathVariable String productCode) {
        return catalogService.productDetail(productCode);
    }
}
