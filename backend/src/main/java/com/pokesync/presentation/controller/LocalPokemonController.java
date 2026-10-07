package com.pokesync.presentation.controller;

import com.pokesync.application.service.LocalPokemonService;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/local-pokemon")
public class LocalPokemonController {
    private final LocalPokemonService pokemon;
    public LocalPokemonController(LocalPokemonService pokemon) { this.pokemon = pokemon; }
    @GetMapping
    public PageResult<LocalPokemon> list(@RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) { return pokemon.list(limit, offset); }
    @GetMapping("/{id}")
    public LocalPokemon find(@PathVariable UUID id) { return pokemon.find(id); }
    @PostMapping
    public ResponseEntity<LocalPokemon> synchronize(@Valid @RequestBody SynchronizeRequest request) {
        var record = pokemon.synchronize(request.pokeApiId(), request.customName(), request.region(), request.internalClassification());
        return ResponseEntity.created(URI.create("/api/v1/local-pokemon/" + record.id())).body(record);
    }
    @PutMapping("/{id}")
    public LocalPokemon update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return pokemon.update(id, request.customName(), request.region(), request.internalClassification());
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pokemon.delete(id); return ResponseEntity.noContent().build();
    }
    public record SynchronizeRequest(@Positive int pokeApiId, @Size(max = 255) String customName,
            @Size(max = 255) String region, @Size(max = 255) String internalClassification) {}
    public record UpdateRequest(@Size(max = 255) String customName, @Size(max = 255) String region,
            @Size(max = 255) String internalClassification) {}
}
