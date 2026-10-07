package com.pokesync.infrastructure.config;

import com.pokesync.application.port.out.LocalPokemonRepository;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.application.service.LocalPokemonService;
import com.pokesync.application.service.PokemonService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PokemonConfiguration {
    @Bean
    PokemonService pokemonService(PokemonProvider provider) { return new PokemonService(provider); }
    @Bean
    LocalPokemonService localPokemonService(LocalPokemonRepository repository, PokemonProvider provider) {
        return new LocalPokemonService(repository, provider);
    }
}
