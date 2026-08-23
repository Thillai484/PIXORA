package com.pixora.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseUserPrincipal implements Principal {
    private Long id;
    private String firebaseUid;
    private String email;
    private String name;
    private String avatarUrl;

    @Override
    public String getName() {
        return firebaseUid;
    }
}
