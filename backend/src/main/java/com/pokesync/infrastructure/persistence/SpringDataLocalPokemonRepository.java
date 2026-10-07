package com.pokesync.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataLocalPokemonRepository extends JpaRepository<LocalPokemonEntity, UUID> {
    boolean existsByPokeApiId(int pokeApiId);
    @Modifying
    @Query("delete from LocalPokemonEntity pokemon where pokemon.id = :id")
    int deleteRecord(@Param("id") UUID id);
}
