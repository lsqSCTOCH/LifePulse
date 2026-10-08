package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IpLocationService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/location")
public class LocationController {
    private final IpLocationService service;
    public LocationController(IpLocationService service) { this.service = service; }
    @GetMapping("/current")
    public Result current(HttpServletRequest request) { return Result.ok(service.locate(request)); }
}
