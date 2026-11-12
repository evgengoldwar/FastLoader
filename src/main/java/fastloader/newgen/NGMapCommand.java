package fastloader.newgen;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;

/**
 * {@code /ngmap <dimension> <radiusInChunks> [blocks|biomes] [startY]} - generates the area around 0,0 and writes a
 * top-down PNG to the world folder. In the Nether the scan starts below the roof, so the map shows the floors.
 */
public class NGMapCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "ngmap";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ngmap <dimension> <radiusInChunks> [blocks|biomes|preview] [startY] [centerX centerZ]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2) throw new WrongUsageException(getCommandUsage(sender));
        int dim = parseInt(sender, args[0]);
        int radius = parseIntBounded(sender, args[1], 1, 128);
        boolean biomes = args.length > 2 && args[2].startsWith("bio");
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(dim);
        if (args.length > 2 && args[2].startsWith("preview")) {
            preview(sender, world, radius);
            return;
        }
        if (args.length > 2 && args[2].startsWith("section")) {
            section(
                sender,
                world,
                radius,
                args.length > 5 ? parseInt(sender, args[4]) : 0,
                args.length > 5 ? parseInt(sender, args[5]) : 0);
            return;
        }
        int startY = args.length > 3 ? parseInt(sender, args[3]) : (dim == -1 ? 100 : 255);
        int ox = args.length > 5 ? parseInt(sender, args[4]) >> 4 : 0;
        int oz = args.length > 5 ? parseInt(sender, args[5]) >> 4 : 0;
        int size = radius * 2 * 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int cx = -radius; cx < radius; cx++) for (int cz = -radius; cz < radius; cz++) {
            Chunk c = world.getChunkProvider()
                .loadChunk(cx + ox, cz + oz);
            // make sure neighbours exist so population runs
            world.getChunkProvider()
                .loadChunk(cx + ox + 1, cz + oz);
            world.getChunkProvider()
                .loadChunk(cx + ox, cz + oz + 1);
            world.getChunkProvider()
                .loadChunk(cx + ox + 1, cz + oz + 1);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int px = (cx + radius) * 16 + x, pz = (cz + radius) * 16 + z;
                if (biomes) {
                    BiomeGenBase b = c.getBiomeGenForWorldCoords(x, z, world.getWorldChunkManager());
                    img.setRGB(px, pz, b.color);
                    continue;
                }
                int y = startY;
                // in the nether skip down to the first air below the roof
                if (dim == -1) while (y > 0 && c.getBlock(x, y, z) != net.minecraft.init.Blocks.air) y--;
                while (y > 0 && c.getBlock(x, y, z)
                    .getMaterial() == Material.air) y--;
                Block b = c.getBlock(x, y, z);
                int meta = c.getBlockMetadata(x, y, z);
                MapColor mc = b.getMapColor(meta);
                int rgb = mc == null ? 0xFF00FF : mc.colorValue;
                if (b.getMaterial() == Material.water) {
                    int depth = 0;
                    while (y - depth > 0 && c.getBlock(x, y - depth, z)
                        .getMaterial() == Material.water) depth++;
                    rgb = scale(0x3F76E4, 1.0 - Math.min(depth, 30) / 45.0);
                } else {
                    double shade = 0.55 + (y / (dim == -1 ? 128.0 : 256.0)) * 0.7;
                    rgb = scale(rgb, Math.min(1.25, shade));
                }
                img.setRGB(px, pz, rgb);
            }
        }
        try {
            File dir = world.getChunkSaveLocation();
            File out = new File(dir, "ngmap_dim" + dim + (biomes ? "_biomes" : "_blocks") + ".png");
            ImageIO.write(img, "png", out);
            sender.addChatMessage(new ChatComponentText("Wrote " + out.getAbsolutePath()));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText("Failed: " + e));
        }
    }

    /** Vertical cut along X through (centerX, centerZ): shows caves, aquifers and terrain profile. */
    private void section(ICommandSender sender, WorldServer world, int radius, int cxBlock, int czBlock) {
        int w = radius * 32, h = world.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < w; i++) {
            int x = cxBlock - w / 2 + i;
            Chunk c = world.getChunkProvider()
                .loadChunk(x >> 4, czBlock >> 4);
            for (int y = 0; y < h; y++) {
                Block b = c.getBlock(x & 15, y, czBlock & 15);
                int rgb;
                if (b.getMaterial() == Material.air) rgb = y > 62 ? 0xA8C8FF : 0x101010;
                else if (b.getMaterial() == Material.water) rgb = 0x2F5FD0;
                else if (b.getMaterial() == Material.lava) rgb = 0xFF6A00;
                else {
                    MapColor mc = b.getMapColor(c.getBlockMetadata(x & 15, y, czBlock & 15));
                    rgb = mc == null ? 0xFF00FF : mc.colorValue;
                }
                img.setRGB(i, h - 1 - y, rgb);
            }
        }
        try {
            File out = new File(world.getChunkSaveLocation(), "ngmap_section.png");
            ImageIO.write(img, "png", out);
            sender.addChatMessage(new ChatComponentText("Wrote " + out.getAbsolutePath()));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText("Failed: " + e));
        }
    }

    /** Fast climate-only preview (no chunks generated): left half heights, right half biomes. 8 blocks per pixel. */
    private void preview(ICommandSender sender, WorldServer world, int radius) {
        if (!(world.getWorldChunkManager() instanceof fastloader.newgen.overworld.OverworldBiomeSource src)) {
            sender.addChatMessage(new ChatComponentText("Not a New Generation overworld"));
            return;
        }
        fastloader.newgen.overworld.TerrainShaper shaper = new fastloader.newgen.overworld.TerrainShaper();
        int size = radius * 4;
        int step = 8;
        BufferedImage img = new BufferedImage(size * 2, size, BufferedImage.TYPE_INT_RGB);
        fastloader.newgen.overworld.Climate.Sample s = new fastloader.newgen.overworld.Climate.Sample();
        int[] hist = new int[17];
        for (int px = 0; px < size; px++) for (int pz = 0; pz < size; pz++) {
            int x = (px - size / 2) * step, z = (pz - size / 2) * step;
            src.climate.sample(x, z, s);
            double h = fastloader.newgen.overworld.TerrainShaper
                .surfaceHeight(shaper.offset(s) + shaper.jaggedness(s) * 0.5);
            hist[Math.max(0, Math.min(16, (int) (h / 16)))]++;
            int rgb;
            if (h < 63) rgb = scale(0x3F76E4, 0.5 + h / 126.0);
            else if (h < 66) rgb = 0xDBD3A0;
            else if (h < 100) rgb = scale(0x5A9A3C, 0.7 + (h - 66) / 100.0);
            else if (h < 150) rgb = scale(0x7A7A50, 0.8 + (h - 100) / 200.0);
            else if (h < 190) rgb = scale(0x8A8A8A, 0.8 + (h - 150) / 150.0);
            else rgb = 0xF4F4F4;
            img.setRGB(px, pz, rgb);
            img.setRGB(px + size, pz, src.getBiomeAtBlock(x, z).color);
        }
        StringBuilder sb = new StringBuilder("height histogram (16-block bins): ");
        for (int i = 0; i < hist.length; i++) sb.append(i * 16)
            .append('=')
            .append(hist[i] * 100 / (size * size))
            .append("% ");
        sender.addChatMessage(new ChatComponentText(sb.toString()));
        try {
            File out = new File(world.getChunkSaveLocation(), "ngmap_preview.png");
            ImageIO.write(img, "png", out);
            sender.addChatMessage(new ChatComponentText("Wrote " + out.getAbsolutePath()));
        } catch (Exception e) {
            sender.addChatMessage(new ChatComponentText("Failed: " + e));
        }
    }

    private static int scale(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((rgb >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
}
