package com.xianfupu.shop.user;

import com.xianfupu.shop.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    List<AddressService.AddressView> list() {
        return addressService.list(CurrentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddressService.AddressView create(@Valid @RequestBody AddressRequest request) {
        return addressService.create(CurrentUser.id(), request.toCommand());
    }

    @PutMapping("/{id}")
    AddressService.AddressView update(@PathVariable long id, @Valid @RequestBody AddressRequest request) {
        return addressService.update(CurrentUser.id(), id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long id) {
        addressService.delete(CurrentUser.id(), id);
    }

    public record AddressRequest(@NotBlank @Size(max = 80) String name,
                                 @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
                                 @NotBlank @Size(max = 255) String detail,
                                 @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                                 @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                                 @DecimalMin("0") BigDecimal distanceKm,
                                 boolean isDefault) {
        AddressService.AddressCommand toCommand() {
            return new AddressService.AddressCommand(name, phone, detail, longitude, latitude, distanceKm, isDefault);
        }
    }
}
