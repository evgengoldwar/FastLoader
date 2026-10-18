package fastloader.profiler;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class ProfilerUtil {

    private ProfilerUtil() {}

    /**
     * Returns the base path of a TextureMap ("textures/blocks", "textures/items"). Read reflectively (it is the only
     * String instance field) so a renamed or obfuscated field can never break loading.
     */
    public static String describeTextureMap(Object textureMap) {
        try {
            for (Class<?> c = textureMap.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == String.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        return String.valueOf(f.get(textureMap));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "#" + Integer.toHexString(System.identityHashCode(textureMap));
    }
}
