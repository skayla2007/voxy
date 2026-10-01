package me.cortex.voxy.client.core.vk.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VkModelAtlasCapacityTest {
    @Test void everyModelFitsItsUnchangedTileCoordinates() {
        int rows = 8;
        for (int model = 0; model < 65536; model++) {
            rows = VkModelAtlasCapacity.rows(rows, model);
            assertTrue(rows >= 8 && rows <= 256);
            assertTrue(((model >>> 8) + 1) <= rows);
            assertEquals(0, rows & (rows - 1));
        }
        assertEquals(256, rows);
    }

    @Test void sparseIdsGrowDirectlyToCapacityAndExistingModelsNeverShrink() {
        assertEquals(8, VkModelAtlasCapacity.rows(8,2047));
        assertEquals(16, VkModelAtlasCapacity.rows(8,2048));
        assertEquals(256, VkModelAtlasCapacity.rows(8,65535));
        assertEquals(128, VkModelAtlasCapacity.rows(128,2));
        assertThrows(IllegalArgumentException.class, () -> VkModelAtlasCapacity.rows(8,65536));
    }
}
