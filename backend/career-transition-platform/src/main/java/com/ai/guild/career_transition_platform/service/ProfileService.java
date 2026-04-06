package com.ai.guild.career_transition_platform.service;

import com.ai.guild.career_transition_platform.dto.ChangePasswordRequest;
import com.ai.guild.career_transition_platform.dto.ProfileResponse;
import com.ai.guild.career_transition_platform.dto.UpdateProfileRequest;
import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.exception.InvalidPasswordException;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final Cloudinary cloudinary;

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

		if (user.getImagePath() != null && !user.getImagePath().isBlank()) {
			try {
				cloudinary.uploader().destroy(
						"profiles/user_" + userId,
						ObjectUtils.asMap("resource_type", "image")
				);
				log.debug("Removed previous profile object key={}", user.getImagePath());
			} catch (Exception e) {
				log.warn("Could not remove previous profile object: {}", e.getMessage());
			}
		}

		try {
			Map uploadResult = cloudinary.uploader().upload(
					file.getBytes(),
					ObjectUtils.asMap(
							"folder", "profiles",
							"public_id", "user_" + userId,
							"overwrite", true,
							"resource_type", "image"
					)
			);
			String url = (String) uploadResult.get("secure_url");
			user.setImagePath(url);
			User saved = userRepository.save(user);
			log.info("Profile photo uploaded for userId={} objectKey={}", userId, url);
			return toProfileResponse(saved);
		} catch (IOException e) {
			log.error("MinIO upload failed for userId={}: {}", userId, e.getMessage(), e);
			throw new IllegalStateException("Failed to upload profile photo", e);
		}
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
}
