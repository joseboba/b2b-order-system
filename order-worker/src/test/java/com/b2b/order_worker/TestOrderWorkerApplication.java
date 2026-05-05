package com.b2b.order_worker;

import org.springframework.boot.SpringApplication;

public class TestOrderWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.from(OrderWorkerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
