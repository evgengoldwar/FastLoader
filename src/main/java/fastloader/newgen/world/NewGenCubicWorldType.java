package fastloader.newgen.world;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;

import com.cardinalstar.cubicchunks.api.IntRange;
import com.cardinalstar.cubicchunks.api.world.ICubicWorldType;
import com.cardinalstar.cubicchunks.api.worldgen.BuiltinWorldDecorators;
import com.cardinalstar.cubicchunks.api.worldgen.IWorldGenerator;
import com.cardinalstar.cubicchunks.worldgen.VanillaWorldGenerator;

/**
 * "New Generation" when Cubic Chunks is installed: the overworld becomes cubic (no bedrock floor, the world continues
 * downwards with Cubic Chunks' deep generation such as the Et Futurum deepslate layer). Only created reflectively when
 * Cubic Chunks is loaded.
 */
public class NewGenCubicWorldType extends NewGenWorldType implements ICubicWorldType {

    @Override
    public IWorldGenerator createCubeGenerator(WorldServer world, IChunkProvider chunkGenerator) {
        return new VanillaWorldGenerator(chunkGenerator, world, BuiltinWorldDecorators.CUBIC_VANILLA);
    }

    @Override
    public IntRange getGenerationRange(World world) {
        return new IntRange(0, 256);
    }

    @Override
    public boolean hasCubicGeneratorForWorld(World world) {
        return world.provider.dimensionId == 0;
    }
}
