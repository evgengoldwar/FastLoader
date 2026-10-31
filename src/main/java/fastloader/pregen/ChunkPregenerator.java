package fastloader.pregen;

import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.DimensionManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import fastloader.FastLoader;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.ProfileReport;
import fastloader.profiler.Section;

/**
 * Simple square-spiral pregenerator, mainly used as a reproducible benchmark for the chunk profiler. It spends at most
 * {@link #tickBudgetNs} per server tick and unloads rings it no longer needs. Rings are kept {@link #KEEP_RINGS} behind
 * the edge: one ring is needed for population, the extra ones keep chunks that neighbour generators (Roguelike,
 * Et Futurum...) read from being unloaded and loaded again.
 */
public final class ChunkPregenerator {

    public static final ChunkPregenerator INSTANCE = new ChunkPregenerator();

    public static volatile long tickBudgetNs = 35_000_000L;
    public static volatile long progressIntervalNs = 10_000_000_000L;
    private static final int KEEP_RINGS = 3;

    private Job job;

    private ChunkPregenerator() {}

    public synchronized String start(int dim, int centerX, int centerZ, int radius) {
        if (job != null) return "A pregeneration is already running: " + job.describe();
        WorldServer world = DimensionManager.getWorld(dim);
        if (world == null) return "Dimension " + dim + " is not loaded";
        job = new Job(dim, centerX, centerZ, radius);
        FastLoader.LOG.info("[pregen] started: {}", job.describe());
        return "Started: " + job.describe();
    }

    public synchronized String stop(ICommandSender sender) {
        if (job == null) return "No pregeneration running";
        String s = "Stopped: " + job.describe();
        FastLoader.LOG.info("[pregen] {}", s);
        job = null;
        return s;
    }

    public synchronized String status() {
        return job == null ? "No pregeneration running" : job.describe();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Job j;
        synchronized (this) {
            j = job;
        }
        if (j == null) return;
        WorldServer world = DimensionManager.getWorld(j.dim);
        if (world == null) {
            FastLoader.LOG.warn("[pregen] dimension {} unloaded, stopping", j.dim);
            synchronized (this) {
                job = null;
            }
            return;
        }
        boolean done;
        ChunkProfiler.push(Section.PREGEN);
        try {
            done = j.run(world);
        } finally {
            ChunkProfiler.pop(Section.PREGEN);
        }
        j.maybeLogProgress();
        if (done) {
            FastLoader.LOG.info("[pregen] finished: {}", j.describe());
            synchronized (this) {
                if (job == j) job = null;
            }
            // tell ops in chat
            List<?> players = world.playerEntities;
            for (Object o : players) {
                if (o instanceof EntityPlayerMP && ((EntityPlayerMP) o).canCommandSenderUseCommand(2, "fastloader")) {
                    ((EntityPlayerMP) o)
                        .addChatMessage(new ChatComponentText("[FastLoader] pregen finished: " + j.describe()));
                }
            }
        }
    }

    private static final class Job {

        final int dim, centerX, centerZ, radius;
        final long total;
        final long startNs = System.nanoTime();
        long lastLogNs = startNs;
        long lastLogDone;
        int ring;
        int index;
        long done;

        Job(int dim, int centerX, int centerZ, int radius) {
            this.dim = dim;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            long side = 2L * radius + 1;
            this.total = side * side;
        }

        /** @return true when finished */
        boolean run(WorldServer world) {
            ChunkProviderServer provider = world.theChunkProviderServer;
            long deadline = System.nanoTime() + tickBudgetNs;
            while (System.nanoTime() < deadline) {
                if (ring > radius) {
                    for (int r = 1; r <= KEEP_RINGS; r++) unloadRing(world, ring - r);
                    return true;
                }
                int ringSize = ring == 0 ? 1 : 8 * ring;
                int[] p = position(ring, index);
                provider.loadChunk(centerX + p[0], centerZ + p[1]);
                done++;
                if (++index >= ringSize) {
                    index = 0;
                    if (ring >= KEEP_RINGS) unloadRing(world, ring - KEEP_RINGS);
                    ring++;
                }
            }
            return false;
        }

        private void unloadRing(WorldServer world, int r) {
            if (r < 0) return;
            int size = r == 0 ? 1 : 8 * r;
            for (int i = 0; i < size; i++) {
                int[] p = position(r, i);
                int x = centerX + p[0], z = centerZ + p[1];
                if (isWatched(world, x, z)) continue;
                world.theChunkProviderServer.unloadChunksIfNotNearSpawn(x, z);
            }
        }

        private static boolean isWatched(WorldServer world, int x, int z) {
            for (Object o : world.playerEntities) {
                if (o instanceof EntityPlayerMP && world.getPlayerManager()
                    .isPlayerWatchingChunk((EntityPlayerMP) o, x, z)) {
                    return true;
                }
            }
            return false;
        }

        /** Position on the square ring r (r > 0 has 8r chunks), walking clockwise from the north-west corner. */
        private static int[] position(int r, int i) {
            if (r == 0) return new int[] { 0, 0 };
            int side = i / (2 * r);
            int off = i % (2 * r);
            switch (side) {
                case 0:
                    return new int[] { -r + off, -r };
                case 1:
                    return new int[] { r, -r + off };
                case 2:
                    return new int[] { r - off, r };
                default:
                    return new int[] { -r, r - off };
            }
        }

        void maybeLogProgress() {
            long now = System.nanoTime();
            if (now - lastLogNs < progressIntervalNs) return;
            double sec = (now - lastLogNs) / 1e9;
            double rate = (done - lastLogDone) / sec;
            double totalRate = done / ((now - startNs) / 1e9);
            long left = total - done;
            FastLoader.LOG.info(
                "[pregen] {} | now {} chunks/s, avg {} chunks/s, ETA {} | {}",
                describe(),
                String.format("%.1f", rate),
                String.format("%.1f", totalRate),
                totalRate > 0 ? formatDuration((long) (left / totalRate)) : "?",
                ProfileReport.shortStatus());
            lastLogNs = now;
            lastLogDone = done;
        }

        String describe() {
            return String.format(
                "dim %d center chunk [%d, %d] radius %d chunks: %d/%d (%.1f%%), ring %d/%d",
                dim,
                centerX,
                centerZ,
                radius,
                done,
                total,
                done * 100.0 / total,
                Math.min(ring, radius),
                radius);
        }

        private static String formatDuration(long s) {
            return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        }
    }
}
