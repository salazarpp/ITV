package com.pokesync.presentation.controller;

import com.pokesync.application.service.PokemonService;
import com.pokesync.domain.model.PageResult;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.PokemonSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pokemon")
public class PokemonController {
    private final PokemonService pokemon;
    public PokemonController(PokemonService pokemon) { this.pokemon = pokemon; }
    @GetMapping
    public PageResult<PokemonSummary> list(@RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) { return pokemon.list(limit, offset); }
    @GetMapping("/{id}")
    public PokemonDetail detail(@PathVariable int id) { return pokemon.find(id); }
}
