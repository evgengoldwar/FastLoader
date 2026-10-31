package fastloader.optimize;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

/**
 * Modern Minecraft's "simulation distance": with a large view distance, 1.7.10 random-ticks every chunk and updates
 * every entity within the whole view distance (65x65 chunks at 32). Beyond {@link #chunks} from every player:
 * <ul>
 * <li>chunks still get Chunk.func_150804_b (light population / "ready to send" flag, needed to show them), but no
 * random block ticks, ice / snow or lightning;</li>
 * <li>non-player entities update once every {@link #FAR_ENTITY_INTERVAL} ticks instead of every tick, so they keep
 * despawning normally (hostile mobs far away) without costing a full update each tick.</li>
 * </ul>
 * Chunks with chunk loader tickets (persistent chunks) and everything near players are untouched. Tile entities are
 * untouched too (machines keep running wherever vanilla runs them).
 */
public final class SimulationDistance {

    private SimulationDistance() {}

    /** 0 = off (vanilla). */
    public static volatile int chunks = 10;
    public static final int FAR_ENTITY_INTERVAL = 20;

    // statistics, server thread only
    public static long farChunksPerTick;
    public static long skippedEntityUpdates;

    /** Called at the end of World.setActivePlayerChunksAndCheckLight (the active set is rebuilt every tick). */
    public static void filterActiveChunks(World world, Set<ChunkCoordIntPair> active) {
        int dist = chunks;
        if (dist <= 0 || !(world instanceof WorldServer) || active.isEmpty()) return;
        List<?> players = world.playerEntities;
        if (players.isEmpty()) return;
        int n = players.size();
        int[] px = new int[n], pz = new int[n];
        for (int i = 0; i < n; i++) {
            EntityPlayer p = (EntityPlayer) players.get(i);
            px[i] = (int) Math.floor(p.posX) >> 4;
            pz[i] = (int) Math.floor(p.posZ) >> 4;
        }
        Set<ChunkCoordIntPair> persistent = world.getPersistentChunks()
            .keySet();
        WorldServer ws = (WorldServer) world;
        long far = 0;
        for (Iterator<ChunkCoordIntPair> it = active.iterator(); it.hasNext();) {
            ChunkCoordIntPair c = it.next();
            if (near(c.chunkXPos, c.chunkZPos, px, pz, dist) || persistent.contains(c)) continue;
            it.remove();
            far++;
            // what the vanilla loop would still have done that matters: light population and the
            // "ticked" flag, without which the chunk is never sent to clients
            if (ws.theChunkProviderServer.chunkExists(c.chunkXPos, c.chunkZPos)) {
                Chunk chunk = ws.getChunkFromChunkCoords(c.chunkXPos, c.chunkZPos);
                chunk.func_150804_b(false);
            }
        }
        farChunksPerTick = far;
    }

    /** @return true if this entity update should be skipped this tick */
    public static boolean skipEntityUpdate(World world, Entity entity) {
        int dist = chunks;
        if (dist <= 0 || entity instanceof EntityPlayer || !(world instanceof WorldServer)) return false;
        if ((world.getTotalWorldTime() + entity.getEntityId()) % FAR_ENTITY_INTERVAL == 0) return false;
        if (entity.riddenByEntity != null) return false; // carrying something (maybe a player)
        int cx = (int) Math.floor(entity.posX) >> 4;
        int cz = (int) Math.floor(entity.posZ) >> 4;
        List<?> players = world.playerEntities;
        if (players.isEmpty()) return false;
        for (Object o : players) {
            EntityPlayer p = (EntityPlayer) o;
            if (Math.abs(((int) Math.floor(p.posX) >> 4) - cx) <= dist
                && Math.abs(((int) Math.floor(p.posZ) >> 4) - cz) <= dist) return false;
        }
        if (world.getPersistentChunks()
            .containsKey(new ChunkCoordIntPair(cx, cz))) return false;
        skippedEntityUpdates++;
        return true;
    }

    private static boolean near(int x, int z, int[] px, int[] pz, int dist) {
        for (int i = 0; i < px.length; i++) {
            if (Math.abs(px[i] - x) <= dist && Math.abs(pz[i] - z) <= dist) return true;
        }
        return false;
    }

    public static String describe(long ticks) {
        return String.format(
            "Simulation distance: %s | far chunks without random ticks: %d per tick | far entity updates skipped: %d (%.0f per tick)",
            chunks <= 0 ? "off" : chunks + " chunks",
            farChunksPerTick,
            skippedEntityUpdates,
            ticks == 0 ? 0 : (double) skippedEntityUpdates / ticks);
    }

    public static void resetStats() {
        skippedEntityUpdates = 0;
    }
}
