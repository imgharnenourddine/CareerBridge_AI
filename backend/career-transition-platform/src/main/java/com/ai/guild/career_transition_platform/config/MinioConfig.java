package com.ai.guild.career_transition_platform.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

	@Bean
	public MinioClient minioClient(
			@Value("${minio.url}") String minioUrl,
			@Value("${minio.access-key}") String accessKey,
			@Value("${minio.secret-key}") String secretKey) {
		return MinioClient.builder()
				.endpoint(minioUrl)
				.credentials(accessKey, secretKey)
				.build();
	}

	@Bean
	public MinioBucketInitializer minioBucketInitializer(
			MinioClient minioClient,
			@Value("${minio.bucket-name}") String bucketName,
			@Value("${minio.verify-bucket-at-startup:true}") boolean verifyBucketAtStartup) {
		return new MinioBucketInitializer(minioClient, bucketName, verifyBucketAtStartup);
	}

	@Slf4j
	public static class MinioBucketInitializer {

		private final MinioClient minioClient;
		private final String bucketName;
		private final boolean verifyBucketAtStartup;

		public MinioBucketInitializer(MinioClient minioClient, String bucketName, boolean verifyBucketAtStartup) {
			this.minioClient = minioClient;
			this.bucketName = bucketName;
			this.verifyBucketAtStartup = verifyBucketAtStartup;
		}

		@PostConstruct
		public void ensureBucketExists() {
			if (!verifyBucketAtStartup) {
				log.info("MinIO bucket verification skipped (minio.verify-bucket-at-startup=false)");
				return;
			}
			try {
				boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
				if (exists) {
					log.info("MinIO bucket already exists: {}", bucketName);
				} else {
					minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
					log.info("MinIO bucket created: {}", bucketName);
				}
			} catch (Exception e) {
				log.error("MinIO bucket setup failed: {}", e.getMessage(), e);
				throw new IllegalStateException("Could not verify or create MinIO bucket: " + bucketName, e);
			}
		}
	}
}
