package com.example.wallet;

import org.springframework.boot.SpringApplication;
import org.testcontainers.utility.TestcontainersConfiguration;

public class TestWalletApplication {

  public static void main(String[] args) {
    SpringApplication.from(WalletApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
