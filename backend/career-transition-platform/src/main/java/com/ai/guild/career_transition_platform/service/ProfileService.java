package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.ChangePasswordRequest;
import com.ai.guild.career_transition_platform.dto.ProfileResponse;
import com.ai.guild.career_transition_platform.dto.UpdateProfileRequest;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InvalidPasswordException;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final MinioClient minioClient;

	@Value("${minio.bucket-name}")
	private String bucketName;

	@Transactional(readOnly = true)
	public ProfileResponse getProfile(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("Profile get: user not found userId={}", userId);
					return new EntityNotFoundException("User not found");
				});
		return toProfileResponse(user);
	}

	@Transactional
	public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("Profile update: user not found userId={}", userId);
					return new EntityNotFoundException("User not found");
				});
		user.setFirstName(request.getFirstName());
		user.setLastName(request.getLastName());
		User saved = userRepository.save(user);
		log.info("Profile updated for userId={} email={}", saved.getId(), saved.getEmail());
		return toProfileResponse(saved);
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("Password change: user not found userId={}", userId);
					return new EntityNotFoundException("User not found");
				});
		if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
			log.warn("Password change rejected: current password incorrect for userId={}", userId);
			throw new InvalidPasswordException("Current password is incorrect");
		}
		user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
		userRepository.save(user);
		log.info("Password changed successfully for userId={}", userId);
	}

	@Transactional
	public ProfileResponse uploadProfilePhoto(Long userId, MultipartFile file) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("Photo upload: user not found userId={}", userId);
					return new EntityNotFoundException("User not found");
				});

		String ext = resolveExtension(file.getOriginalFilename());
		String objectName = "profile_" + userId + "_" + System.currentTimeMillis() + "." + ext;

		if (user.getImagePath() != null && !user.getImagePath().isBlank()) {
			try {
				minioClient.removeObject(
						RemoveObjectArgs.builder().bucket(bucketName).object(user.getImagePath()).build());
				log.debug("Removed previous profile object key={}", user.getImagePath());
			} catch (Exception e) {
				log.warn("Could not remove previous profile object: {}", e.getMessage());
			}
		}

		try (InputStream inputStream = file.getInputStream()) {
			String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(bucketName)
							.object(objectName)
							.stream(inputStream, file.getSize(), -1)
							.contentType(contentType)
							.build());
		} catch (Exception e) {
			log.error("MinIO upload failed for userId={}: {}", userId, e.getMessage(), e);
			throw new IllegalStateException("Failed to upload profile photo", e);
		}

		user.setImagePath(objectName);
		User saved = userRepository.save(user);
		log.info("Profile photo uploaded for userId={} objectKey={}", userId, objectName);
		return toProfileResponse(saved);
	}

	@Transactional(readOnly = true)
	public Optional<ProfilePhotoContent> getProfilePhoto(Long userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> {
					log.warn("Photo download: user not found userId={}", userId);
					return new EntityNotFoundException("User not found");
				});
		if (user.getImagePath() == null || user.getImagePath().isBlank()) {
			log.debug("No profile photo stored for userId={}", userId);
			return Optional.empty();
		}
		try (InputStream stream = minioClient.getObject(
				GetObjectArgs.builder()
						.bucket(bucketName)
						.object(user.getImagePath())
						.build())) {
			byte[] bytes = stream.readAllBytes();
			String contentType = guessImageContentType(user.getImagePath());
			log.debug("Profile photo loaded from MinIO userId={} bytes={} contentType={}", userId, bytes.length, contentType);
			return Optional.of(new ProfilePhotoContent(bytes, contentType));
		} catch (Exception e) {
			log.error("MinIO getObject failed for userId={}: {}", userId, e.getMessage(), e);
			throw new IllegalStateException("Failed to load profile photo", e);
		}
	}

	private static String resolveExtension(String originalFilename) {
		if (originalFilename == null || !originalFilename.contains(".")) {
			return "jpg";
		}
		return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
	}

	private static String guessImageContentType(String objectKey) {
		String lower = objectKey.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".png")) {
			return "image/png";
		}
		if (lower.endsWith(".gif")) {
			return "image/gif";
		}
		if (lower.endsWith(".webp")) {
			return "image/webp";
		}
		if (lower.endsWith(".svg")) {
			return "image/svg+xml";
		}
		return "image/jpeg";
	}

	private static ProfileResponse toProfileResponse(User user) {
		return ProfileResponse.builder()
				.id(user.getId())
				.email(user.getEmail())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.imagePath(user.getImagePath())
				.build();
	}

	public record ProfilePhotoContent(byte[] data, String contentType) {
	}
}
