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
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "sort", defaultValue = "default") String sort,
            @RequestParam(name = "specialOnly", defaultValue = "false") boolean specialOnly,
            @RequestParam(name = "memberOnly", defaultValue = "false") boolean memberOnly,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return catalogService.products(category, keyword, sort, specialOnly, memberOnly, page, size);
    }

    @GetMapping("/products/{productCode}")
    CatalogService.ProductDetail product(@PathVariable("productCode") String productCode) {
        return catalogService.productDetail(productCode);
    }
}
