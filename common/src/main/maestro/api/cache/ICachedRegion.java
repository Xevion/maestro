package maestro.api.cache;

public interface ICachedRegion extends IBlockTypeAccess {

    /**
     * Returns whether the block at the specified X and Z coordinates is cached in this world.
     * Similar to {@link ICachedWorld#isCached(int, int)}, however, the block coordinates should in
     * on a scale from 0 to 511 (inclusive) because region sizes are 512x512 blocks.
     *
     * @param blockX The block X coordinate
     * @param blockZ The block Z coordinate
     * @return Whether the specified XZ location is cached
     * @see ICachedWorld#isCached(int, int)
     */
    boolean isCached(int blockX, int blockZ);

    /**
     * @return The X coordinate of this region
     */
    int getX();

    /**
     * @return The Z coordinate of this region
     */
    int getZ();
}
