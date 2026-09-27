package me.cortex.voxy.client;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.common.world.service.VoxelIngestService;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

//Feeds voxy's ingest service from vanilla client chunk events. Voxy normally ingests through sodium's chunk
// tracker; this path is used instead when sodium is absent or voxy runs headless (another mod renders the world).
//
// A freshly received chunk usually has no lighting yet and the ingest service skips unlit chunks, so loads are
// retried after a short delay, and every chunk is ingested once more when it unloads (its final state).
public final class VanillaChunkIngest {
    private static final int FIRST_ATTEMPT_DELAY_TICKS = 20;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final int MAX_ATTEMPTS = 4;
    private static final int MAX_INGESTS_PER_TICK = 64;

    //chunk pos -> packed (attempts << 16 | ticks until next attempt), client thread only
    private static final Long2IntOpenHashMap PENDING = new Long2IntOpenHashMap();
    private static ClientLevel pendingLevel;

    private VanillaChunkIngest() {
    }

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!isActive()) return;
            if (pendingLevel != level) {
                PENDING.clear();
                pendingLevel = level;
            }
            PENDING.put(pack(chunk.getPos().x(), chunk.getPos().z()), FIRST_ATTEMPT_DELAY_TICKS);
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            if (!isActive()) return;
            PENDING.remove(pack(chunk.getPos().x(), chunk.getPos().z()));
            VoxelIngestService.tryAutoIngestChunk(chunk);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static long pack(int x, int z) {
        return (x & 0xFFFFFFFFL) | ((long) z << 32);
    }

    private static boolean isActive() {
        return VoxyClient.useVanillaIngest() && VoxyCommon.getInstance() != null && VoxyConfig.CONFIG.ingestEnabled;
    }

    private static void tick() {
        if (PENDING.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != pendingLevel || !isActive()) {
            PENDING.clear();
            pendingLevel = level;
            return;
        }
        int budget = MAX_INGESTS_PER_TICK;
        var it = PENDING.long2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2IntMap.Entry entry = it.next();
            int packed = entry.getIntValue();
            int delay = (packed & 0xFFFF) - 1;
            int attempts = packed >>> 16;
            if (delay > 0 || budget <= 0) {
                entry.setValue((attempts << 16) | Math.max(delay, 1));
                continue;
            }
            long pos = entry.getLongKey();
            LevelChunk chunk = level.getChunkSource().getChunk((int) pos, (int) (pos >> 32), ChunkStatus.FULL, false);
            if (chunk == null) {
                it.remove();
                continue;
            }
            budget--;
            boolean ingested = VoxelIngestService.tryAutoIngestChunk(chunk);
            if (ingested || attempts + 1 >= MAX_ATTEMPTS) {
                it.remove();
            } else {
                entry.setValue(((attempts + 1) << 16) | RETRY_DELAY_TICKS);
            }
        }
    }
}
