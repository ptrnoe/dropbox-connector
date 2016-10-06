package org.dropbox.main;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.jasypt.exceptions.EncryptionOperationNotPossibleException;
import org.jasypt.util.text.StrongTextEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.MapJobRepositoryFactoryBean;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;

@Configuration
@EnableBatchProcessing
@EnableAutoConfiguration
public class DropboxConnectorConfiguration {

	@Value("${tokentDropbox}")
	private String tokentDropbox;

	protected static final String dots = "...";
	protected static final String slash = "/";

	@Autowired
	private JobBuilderFactory jobBuilderFactory;

	@Autowired
	private StepBuilderFactory stepBuilderFactory;

	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Value("${torrentsDir}")
	protected String torrentsDir;

	@Value("${remoteUploadedDir}")
	protected String remoteUploadedDir;

	@Value("${remoteDownloadedDir}")
	protected String remoteDownloadedDir;

	private String passwordEncryption;

	/**
	 * No job DATABASE
	 * @return
	 */
	@Bean
	public PlatformTransactionManager getTransactionManager() {
		return new ResourcelessTransactionManager();
	}

	@Bean
	public JobRepository getJobRepo() {
		try {
			return new MapJobRepositoryFactoryBean(getTransactionManager()).getObject();
		} catch (Exception e) {
			return null;
		}
	}

	@Bean
	public Step getFiles() {
		return stepBuilderFactory.get("getFiles")
				.tasklet(new Tasklet() {
					public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws DbxException {
						logger.info("");
						logger.info("Inicio getFiles"+dots);
						passwordEncryption=System.getProperty("passwordEncryption");
						boolean passwordDecrypted = false;
						if (passwordEncryption!=null && !passwordEncryption.isEmpty()){
							StrongTextEncryptor textEncryptor = new StrongTextEncryptor();
							textEncryptor.setPassword(passwordEncryption);
							String ACCESS_TOKEN = null;
							try{
								ACCESS_TOKEN = textEncryptor.decrypt(tokentDropbox);
								passwordDecrypted=true;
							} catch (EncryptionOperationNotPossibleException e){
								logger.error("No se ha podido desenciptar el token!!!");
							}

							if (passwordDecrypted){
								DbxRequestConfig builder = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
								DbxClientV2 client = new DbxClientV2(builder, ACCESS_TOKEN);
								
								FullAccount account = client.users().getCurrentAccount();
								logger.info("Account name: "+account.getName().getDisplayName());
								
								ListFolderResult result = client.files().listFolder(remoteUploadedDir);
								while (true) {
									for (Metadata metadata : result.getEntries()) {
										File file = new File(torrentsDir+metadata.getName());
										file.getParentFile().mkdirs();
										if (!file.exists()){
											OutputStream out = null;
											try{
												out = new FileOutputStream(file);
												logger.info("Descargando fichero: "+metadata.getPathDisplay()+dots);
												client.files().downloadBuilder(metadata.getPathDisplay()).download(out);
												String origenRemoto = remoteUploadedDir + slash + metadata.getName();
												String destinoRemoto = remoteDownloadedDir + slash + metadata.getName();
												logger.info("Moviendo fichero: \""+origenRemoto+"\" a \""+destinoRemoto+"\""+dots);
												client.files().move(origenRemoto, destinoRemoto);
												logger.info("¡¡¡Fichero descargado: "+metadata.getPathLower()+"!!!");
											} catch (IOException ioe){
												logger.error(ioe.toString());
											} finally {
												try {
													if (out!=null){
														out.close();
													}
												} catch (IOException e) {
													logger.error(e.toString());
												}
											}
										} else {
											logger.warn("Fichero no descargado por existir en local: "+metadata.getPathLower());
										}
									}
									
									if (!result.getHasMore()) {
										break;
									}
									
									result = client.files().listFolderContinue(result.getCursor());
								}
								
								logger.info("Final getFiles...");
								logger.info("");
							}
						} else {
							logger.error("Parametro passwordEncryption no definido!!!");
						}
						return null;
					}
				})
				.build();
	}

	@Bean
	public Job job(Step getFiles) throws Exception {
		return jobBuilderFactory.get("getFiles")
				.incrementer(new RunIdIncrementer())
				.start(getFiles)
				.build();
	}
}
