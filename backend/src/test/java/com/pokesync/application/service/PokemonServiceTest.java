package com.pokesync.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.pokesync.application.exception.InvalidPokemonRequestException;
import com.pokesync.application.port.out.PokemonProvider;
import com.pokesync.domain.model.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PokemonServiceTest {
    private final PokemonProvider provider = mock(PokemonProvider.class);
    private final PokemonService service = new PokemonService(provider);
    @Test void preservesPaginationCountAndOffset() {
        var page = new PageResult<com.pokesync.domain.model.PokemonSummary>(1302, List.of());
        when(provider.list(20, 7)).thenReturn(page);
        assertThat(service.list(20, 7)).isSameAs(page);
        verify(provider).list(20, 7);
    }
    @Test void rejectsUnboundedOrInvalidPaginationBeforeProviderAccess() {
        for (int limit : new int[] {0, -1, 101}) {
            assertThatThrownBy(() -> service.list(limit, 0)).isInstanceOf(InvalidPokemonRequestException.class);
        }
        assertThatThrownBy(() -> service.list(20, -1)).isInstanceOf(InvalidPokemonRequestException.class);
        assertThatThrownBy(() -> service.find(0)).isInstanceOf(InvalidPokemonRequestException.class);
        verifyNoInteractions(provider);
    }
}
