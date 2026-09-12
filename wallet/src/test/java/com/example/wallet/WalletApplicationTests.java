package com.example.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(LocalTestcontainersConfig.class)
@SpringBootTest
class WalletApplicationTests {

  @Test
  void contextLoads() {}
}
