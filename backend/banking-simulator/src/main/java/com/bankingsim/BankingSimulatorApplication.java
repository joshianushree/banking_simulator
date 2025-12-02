package com.bankingsim;

//import com.bankingsim.ui.BankingConsole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Minimal Spring Boot entry point.
 * Delegates to BankingConsole.start() which provides the full CLI (admin/customer).
 *
 * Replace the previous, more complex BankingSimulatorApplication with this file
 * so only your BankingConsole interactive UI runs on startup.
 */
@SpringBootApplication
public class BankingSimulatorApplication implements CommandLineRunner {

//	@Autowired
//	private BankingConsole bankingConsole;

	public static void main(String[] args) {
		SpringApplication.run(BankingSimulatorApplication.class, args);
	}

	@Override
	public void run(String... args) {
		// ❌ Disable CLI completely (frontend will handle UI)
		// bankingConsole.start();

		System.out.println("Spring Boot backend running at http://localhost:8080");
	}

}
