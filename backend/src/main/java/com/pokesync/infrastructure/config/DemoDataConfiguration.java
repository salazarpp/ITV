package com.pokesync.infrastructure.config;

import com.pokesync.application.port.out.LocalPokemonRepository;
import com.pokesync.application.port.out.PasswordHasher;
import com.pokesync.application.port.out.UserRepository;
import com.pokesync.domain.model.EvolutionPokemon;
import com.pokesync.domain.model.LocalPokemon;
import com.pokesync.domain.model.PokemonDetail;
import com.pokesync.domain.model.PokemonStat;
import com.pokesync.domain.model.StoredPokemon;
import com.pokesync.domain.model.User;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoDataConfiguration {
    private static final Logger log = LoggerFactory.getLogger(DemoDataConfiguration.class);

    @Bean
    ApplicationRunner seedDemo(UserRepository users, PasswordHasher passwords, LocalPokemonRepository pokemon) {
        return args -> {
            if (!users.existsByUsernameOrEmail("demo", "demo@example.com")) {
                users.save(new User(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        "demo", "demo@example.com", passwords.hash("DemoTrainer123!")));
            }
            if (!pokemon.existsByPokeApiId(1)) {
                String image = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png";
                var detail = new PokemonDetail(1, "bulbasaur", image, "Seed Pokemon", 6.9,
                        List.of("overgrow", "chlorophyll"),
                        List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49),
                                new PokemonStat("defense", 49), new PokemonStat("special-attack", 65),
                                new PokemonStat("special-defense", 65), new PokemonStat("speed", 45)),
                        "A seed grows on its back. This fixture is available without a provider request.",
                        List.of(new EvolutionPokemon(1, "bulbasaur"), new EvolutionPokemon(2, "ivysaur"),
                                new EvolutionPokemon(3, "venusaur")));
                var record = new LocalPokemon(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        1, "bulbasaur", image, "Garden starter", "Kanto", "Starter");
                pokemon.create(new StoredPokemon(record, detail));
            }
            log.info("POKESYNC-DEMO-0001 | Demo fixtures available; demo profile is active");
        };
    }
}
