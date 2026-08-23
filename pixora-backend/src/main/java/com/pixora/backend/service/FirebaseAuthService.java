package com.pixora.backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.pixora.backend.config.FirebaseConfig;
import com.pixora.backend.dto.FirebaseUserPrincipal;
import com.pixora.backend.dto.UserResponse;
import com.pixora.backend.entity.User;
import com.pixora.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirebaseAuthService {

    private final UserRepository userRepository;
    private final FirebaseConfig firebaseConfig;
    @Nullable
    private final FirebaseAuth firebaseAuth;

    /**
     * Verify a raw Firebase JWT ID Token
     */
    public FirebaseUserPrincipal verifyToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }

        // Production / Live Firebase verification
        if (firebaseConfig.isInitialized() && firebaseAuth != null) {
            try {
                FirebaseToken decodedToken = firebaseAuth.verifyIdToken(idToken);
                String uid = decodedToken.getUid();
                String email = decodedToken.getEmail();
                String name = decodedToken.getName();
                String picture = decodedToken.getPicture();

                User user = syncUserInternal(uid, email, name, picture);

                return FirebaseUserPrincipal.builder()
                        .id(user.getId())
                        .firebaseUid(uid)
                        .email(email != null ? email : user.getEmail())
                        .name(name != null ? name : user.getName())
                        .avatarUrl(picture != null ? picture : user.getAvatarUrl())
                        .build();
            } catch (Exception e) {
                log.error("Firebase token verification failed: {}", e.getMessage());
                throw new SecurityException("Invalid or expired Firebase ID token: " + e.getMessage(), e);
            }
        }

        // Fallback / Development mode verification
        log.warn("Firebase not configured; using dev token validator for idToken");
        String uid = "dev-user-" + Math.abs(idToken.hashCode());
        String email = "dev@pixora.app";
        String name = "Dev User";
        String avatarUrl = "";

        if (idToken.startsWith("test-token-")) {
            String suffix = idToken.replace("test-token-", "");
            uid = "test-uid-" + suffix;
            email = suffix + "@pixora.app";
            name = "Test User " + suffix;
        }

        User user = syncUserInternal(uid, email, name, avatarUrl);

        return FirebaseUserPrincipal.builder()
                .id(user.getId())
                .firebaseUid(uid)
                .email(email)
                .name(name)
                .avatarUrl(avatarUrl)
                .build();
    }

    /**
     * Synchronize Firebase user details into the local PostgreSQL/JPA database
     */
    @Transactional
    public User syncUserInternal(String firebaseUid, String email, String name, String avatarUrl) {
        Optional<User> existingUser = userRepository.findByFirebaseUid(firebaseUid);

        if (existingUser.isEmpty() && email != null) {
            existingUser = userRepository.findByEmail(email);
        }

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            boolean updated = false;

            if (user.getFirebaseUid() == null || !user.getFirebaseUid().equals(firebaseUid)) {
                user.setFirebaseUid(firebaseUid);
                updated = true;
            }
            if (name != null && !name.isBlank() && !name.equals(user.getName())) {
                user.setName(name);
                updated = true;
            }
            if (avatarUrl != null && !avatarUrl.isBlank() && !avatarUrl.equals(user.getAvatarUrl())) {
                user.setAvatarUrl(avatarUrl);
                updated = true;
            }
            if (email != null && !email.isBlank() && !email.equals(user.getEmail())) {
                user.setEmail(email);
                updated = true;
            }

            return updated ? userRepository.save(user) : user;
        }

        // Create new User record
        User newUser = User.builder()
                .firebaseUid(firebaseUid)
                .email(email != null ? email : firebaseUid + "@placeholder.pixora")
                .name(name != null ? name : "Pixora User")
                .avatarUrl(avatarUrl)
                .build();

        return userRepository.save(newUser);
    }

    /**
     * Public API method to synchronize and return UserResponse DTO
     */
    @Transactional
    public UserResponse syncGoogleUser(FirebaseUserPrincipal principal) {
        User user = syncUserInternal(
                principal.getFirebaseUid(),
                principal.getEmail(),
                principal.getName(),
                principal.getAvatarUrl()
        );

        return mapToUserResponse(user);
    }

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .email(user.getEmail())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
