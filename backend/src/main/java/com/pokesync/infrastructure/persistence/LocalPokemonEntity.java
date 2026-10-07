package com.pokesync.infrastructure.persistence;

import com.pokesync.domain.model.LocalPokemon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "local_pokemon")
public class LocalPokemonEntity implements Persistable<UUID> {
    @Id private UUID id;
    @Column(name = "poke_api_id", nullable = false, unique = true) private int pokeApiId;
    @Column(nullable = false) private String name;
    @Column(length = 2048) private String image;
    @Column(name = "custom_name") private String customName;
    private String region;
    @Column(name = "internal_classification") private String internalClassification;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text") private String snapshotJson;
    @Transient private boolean newEntity = true;

    protected LocalPokemonEntity() {}
    public LocalPokemonEntity(LocalPokemon pokemon, String snapshotJson) {
        id = pokemon.id(); pokeApiId = pokemon.pokeApiId(); name = pokemon.name(); image = pokemon.image();
        customName = pokemon.customName(); region = pokemon.region();
        internalClassification = pokemon.internalClassification(); this.snapshotJson = snapshotJson;
    }
    public LocalPokemon toDomain() {
        return new LocalPokemon(id, pokeApiId, name, image, customName, region, internalClassification);
    }
    public void updateFields(String customName, String region, String classification) {
        this.customName = customName; this.region = region; this.internalClassification = classification;
    }
    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return newEntity; }
    @PostLoad @PostPersist void markPersisted() { newEntity = false; }
}
