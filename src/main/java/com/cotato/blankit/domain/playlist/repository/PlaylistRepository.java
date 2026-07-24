package com.cotato.blankit.domain.playlist.repository;

import com.cotato.blankit.domain.playlist.entity.Playlist;
import com.cotato.blankit.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findByUser(User user);
}
