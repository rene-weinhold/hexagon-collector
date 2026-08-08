package com.weinhold.hexagon.web;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A driving adapter that is both a jMolecules {@code @PrimaryAdapter} and a real Spring MVC
 * controller, used to exercise the HTTP-inbound contact-point detector end to end.
 */
@RestController
@PrimaryAdapter(name = "Orders API")
public class OrdersApiController {

    @PostMapping("/api/orders")
    String place() {
        return "created";
    }

    @GetMapping("/api/orders/{id}")
    String get(@PathVariable String id) {
        return id;
    }

    /** No method condition, so the route answers all of them and the key can only wildcard. */
    @RequestMapping("/api/orders/search")
    String search() {
        return "[]";
    }

}
