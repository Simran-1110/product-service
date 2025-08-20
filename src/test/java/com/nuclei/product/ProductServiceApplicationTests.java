package com.nuclei.product;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ProductServiceApplication.class, properties = {
    "grpc.server.port=0"
})
class ProductServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
