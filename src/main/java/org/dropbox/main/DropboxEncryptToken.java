package org.dropbox.main;

import org.jasypt.util.text.StrongTextEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DropboxEncryptToken {
	
	static Logger logger = LoggerFactory.getLogger(DropboxEncryptToken.class);
	
	public static void main (String args[]){
		StrongTextEncryptor textEncryptor = new StrongTextEncryptor();
		if (args.length==2){
			String passwordEncryption = args[0];
			textEncryptor.setPassword(passwordEncryption);
			String tokentDropbox = args[1];
			logger.info("Password encrypted: "+textEncryptor.encrypt(tokentDropbox));
		} else {
			logger.error("Modo de empleo: java -jar <jar_name>.jar org.dropbox.main.DropboxEncryptToken [passwordEncryption] [tokentDropbox]");
		}
	}
}
