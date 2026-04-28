package com.publicnext.orders.repository;

import com.publicnext.orders.domain.Inventory;
import com.publicnext.orders.persistence.BaseDataJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryRepositoryTest extends BaseDataJpaTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void seedDataIsLoaded() {
        List<Inventory> all = inventoryRepository.findAll();
        assertThat(all).hasSize(5);
    }

    @Test
    void findById_returnsSeededProduct() {
        Optional<Inventory> found = inventoryRepository.findById(
                UUID.fromString("11111111-1111-1111-1111-111111111111"));

        assertThat(found).isPresent();
        assertThat(found.get().getAvailableStock()).isEqualTo(100);
    }

    @Test
    void findAllById_filtersToRequested() {
        List<UUID> ids = List.of(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("99999999-9999-9999-9999-999999999999")  // not seeded
        );

        List<Inventory> found = inventoryRepository.findAllById(ids);

        assertThat(found).hasSize(2);
        assertThat(found).extracting(Inventory::getAvailableStock)
                .containsExactlyInAnyOrder(100, 10);
    }
}
