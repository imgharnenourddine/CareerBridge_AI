package com.ai.guild.career_transition_platform.security;

import com.ai.guild.career_transition_platform.entity.User;
import com.ai.guild.career_transition_platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

	private final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		log.debug("Loading user by email={}", email);
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> {
					log.warn("User not found for email={}", email);
					return new UsernameNotFoundException("User not found: " + email);
				});
		log.debug("User loaded id={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
		return new org.springframework.security.core.userdetails.User(
				user.getEmail(),
				user.getPasswordHash(),
				List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
		);
	}
}
