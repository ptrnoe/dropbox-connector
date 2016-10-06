package org.dropbox.main;
import org.springframework.boot.SpringApplication;

public class DropboxConnectorApp {
	public static void main(String [] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(
				DropboxConnectorConfiguration.class, args)));
	}
}
