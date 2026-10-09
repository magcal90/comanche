package io.github.magcal90.comanche_device_simulator.identity;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7MessageIdGeneratorTest {
    @Test
    void nextId_shouldPrefixDeviceAndGenerateUuidVersionSeven() {
        String id = new UuidV7MessageIdGenerator().nextId("KIOSK-001");
        UUID uuid = UUID.fromString(id.substring("KIOSK-001-".length()));

        assertThat(id).startsWith("KIOSK-001-");
        assertThat(uuid.version()).isEqualTo(7);
    }

    @Test
    void nextId_acrossCategories_shouldRemainUnique() {
        var generator = new UuidV7MessageIdGenerator();
        assertThat(java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(ignored -> generator.nextId("KIOSK-001")))
                .doesNotHaveDuplicates();
    }
}
