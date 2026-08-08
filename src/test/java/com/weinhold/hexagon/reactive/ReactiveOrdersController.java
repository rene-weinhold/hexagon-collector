package com.weinhold.hexagon.reactive;

import org.jmolecules.architecture.hexagonal.PrimaryAdapter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/** A driving adapter on the reactive stack, to exercise the WebFlux inbound detector. */
@RestController
@PrimaryAdapter(name = "Orders API")
public class ReactiveOrdersController {

    @PostMapping("/api/orders")
    Mono<String> place() {
        return Mono.just("created");
    }

    @GetMapping("/api/orders/{id}")
    Mono<String> get(@PathVariable String id) {
        return Mono.just(id);
    }

}
