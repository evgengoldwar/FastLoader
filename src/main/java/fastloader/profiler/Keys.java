package fastloader.profiler;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.biome.BiomeGenBase;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * Builds (and caches) the human readable section keys for things that are only known at runtime: chunk providers,
 * world generators, map generators, biomes. Each key also names the mod that owns the class, so the report directly
 * says which mod is slow.
 */
public final class Keys {

    private Keys() {}

    private static volatile Map<String, String> sourceToMod;

    private static final ClassValue<String> MOD_OF = new ClassValue<String>() {

        @Override
        protected String computeValue(Class<?> type) {
            return resolveMod(type);
        }
    };

    private static final ClassValue<String> PROVIDE = prefixed(Section.TERRAIN.label);
    private static final ClassValue<String> POPULATE = prefixed("populate");
    private static final ClassValue<String> RECREATE = prefixed("recreateStructures");
    private static final ClassValue<String> WORLDGEN = prefixed("IWorldGenerator");
    private static final ClassValue<String> MAPGEN = prefixed("MapGen");
    private static final ClassValue<String> STRUCTURE = prefixed("Structure");

    // keyed by instance: with EndlessIDs biome ids are not limited to 0..255
    private static final ConcurrentHashMap<BiomeGenBase, String> BIOMES = new ConcurrentHashMap<>();

    public static String provide(Object provider) {
        return provider == null ? "provideChunk (null provider)" : PROVIDE.get(provider.getClass());
    }

    public static String populate(Object provider) {
        return provider == null ? "populate (null provider)" : POPULATE.get(provider.getClass());
    }

    public static String recreate(Object provider) {
        return provider == null ? "recreateStructures (null provider)" : RECREATE.get(provider.getClass());
    }

    public static String worldGenerator(Object generator) {
        return generator == null ? "IWorldGenerator (null)" : WORLDGEN.get(generator.getClass());
    }

    public static String mapGen(Object mapGen) {
        return MAPGEN.get(mapGen.getClass());
    }

    public static String structure(Object structure) {
        return STRUCTURE.get(structure.getClass());
    }

    public static String biomeDecorate(BiomeGenBase biome) {
        if (biome == null) return Section.DECORATE.label;
        String k = BIOMES.get(biome);
        if (k == null) {
            k = Section.DECORATE.label + " biome=" + biome.biomeName + " [" + modOf(biome.getClass()) + "]";
            BIOMES.put(biome, k);
        }
        return k;
    }

    public static String modOf(Class<?> type) {
        return MOD_OF.get(type);
    }

    public static String describeClass(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("net.minecraft.")) {
            int dot = name.lastIndexOf('.');
            return name.substring(dot + 1);
        }
        return name;
    }

    private static ClassValue<String> prefixed(final String prefix) {
        return new ClassValue<String>() {

            @Override
            protected String computeValue(Class<?> type) {
                return prefix + ": " + describeClass(type) + " [" + modOf(type) + "]";
            }
        };
    }

    private static String resolveMod(Class<?> type) {
        String name = type.getName();
        if (name.startsWith("net.minecraft.")) return "minecraft";
        if (name.startsWith("net.minecraftforge.")) return "forge";
        if (name.startsWith("cpw.mods.fml.")) return "fml";
        try {
            CodeSource cs = type.getProtectionDomain() == null ? null
                : type.getProtectionDomain()
                    .getCodeSource();
            URL url = cs == null ? null : cs.getLocation();
            if (url != null) {
                String path = normalize(url);
                Map<String, String> map = sourceMap();
                for (Map.Entry<String, String> e : map.entrySet()) {
                    String key = e.getKey();
                    if (path.equals(key) || path.startsWith(key + "/")) return e.getValue();
                }
            }
        } catch (Throwable ignored) {}
        // fall back to the first two package segments
        String[] parts = name.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : name;
    }

    private static Map<String, String> sourceMap() {
        Map<String, String> map = sourceToMod;
        if (map == null) {
            map = new HashMap<>();
            for (ModContainer mc : Loader.instance()
                .getModList()) {
                File src = mc.getSource();
                if (src == null) continue;
                try {
                    String p = normalize(
                        src.getCanonicalFile()
                            .toURI()
                            .toURL());
                    // several mods can live in one jar, keep the first (usually the main one)
                    if (!map.containsKey(p)) map.put(p, mc.getModId());
                } catch (Exception ignored) {}
            }
            if (!map.isEmpty()) sourceToMod = map;
        }
        return map;
    }

    private static String normalize(URL url) {
        String s = url.toString();
        if (s.startsWith("jar:")) s = s.substring(4);
        int bang = s.indexOf("!/");
        if (bang >= 0) s = s.substring(0, bang);
        try {
            s = java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception ignored) {}
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
