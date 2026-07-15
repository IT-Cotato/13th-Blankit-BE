package com.cotato.blankit.global.security;

import com.cotato.blankit.domain.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String socialProvider;
    private final String socialId;

    private CustomUserDetails(User user) {
        this.userId = user.getId();
        this.socialProvider = user.getSocialProvider().name();
        this.socialId = user.getSocialId();
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
