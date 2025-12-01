package maestro.utils

import maestro.Agent
import maestro.api.utils.IPlayerContext
import maestro.cache.CachedRegion
import maestro.cache.WorldData
import maestro.utils.accessor.IClientChunkProvider
import maestro.utils.pathing.BetterWorldBorder
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus

/** Wraps get for chunk caching capability */
class BlockStateInterface
    @JvmOverloads
    constructor(
        ctx: IPlayerContext,
        copyLoadedChunks: Boolean = false,
    ) {
        private val provider: ClientChunkCache
        private val worldData: WorldData?

        @JvmField
        val world: Level

        @JvmField
        val isPassableBlockPos: BlockPos.MutableBlockPos

        @JvmField
        val access: BlockGetter

        @JvmField
        val worldBorder: BetterWorldBorder

        private var prev: LevelChunk? = null
        private var prevCached: CachedRegion? = null

        private val useTheRealWorld: Boolean

        init {
            this.world = ctx.world()
            this.worldBorder = BetterWorldBorder(world.worldBorder)
            this.worldData = ctx.worldData() as WorldData?

            this.provider =
                if (copyLoadedChunks) {
                    (world.chunkSource as IClientChunkProvider).createThreadSafeCopy()
                } else {
                    world.chunkSource as ClientChunkCache
                }

            this.useTheRealWorld = !Agent.settings().pathThroughCachedOnly.value

            if (!ctx.minecraft().isSameThread) {
                throw IllegalStateException("BlockStateInterface must be constructed on the main thread")
            }

            this.isPassableBlockPos = BlockPos.MutableBlockPos()
            this.access = BlockStateInterfaceAccessWrapper(this)
        }

        fun worldContainsLoadedChunk(
            blockX: Int,
            blockZ: Int,
        ): Boolean = provider.hasChunk(blockX shr 4, blockZ shr 4)

        fun get0(pos: BlockPos): BlockState = get0(pos.x, pos.y, pos.z)

        fun get0(
            x: Int,
            y: Int,
            z: Int,
        ): BlockState {
            val adjustedY = y - world.dimensionType().minY()

            // Invalid vertical position
            if (adjustedY < 0 || adjustedY >= world.dimensionType().height()) {
                return AIR
            }

            if (useTheRealWorld) {
                val cached = prev
                // There's great cache locality in block state lookups
                // Generally it's within each movement
                // If it's the same chunk as last time
                // We can just skip the mc.world.getChunk lookup
                // which is a Long2ObjectOpenHashMap.get
                // see issue #113
                if (cached != null && cached.pos.x == x shr 4 && cached.pos.z == z shr 4) {
                    return getFromChunk(cached, x, adjustedY, z)
                }

                val chunk = provider.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)
                if (chunk != null && !chunk.isEmpty) {
                    prev = chunk
                    return getFromChunk(chunk, x, adjustedY, z)
                }
            }

            // Same idea here, skip the Long2ObjectOpenHashMap.get if at all possible
            // Except here, it's 512x512 tiles instead of 16x16, so even better repetition
            var cached = prevCached
            if (cached == null || cached.x != x shr 9 || cached.z != z shr 9) {
                if (worldData == null) {
                    return AIR
                }

                val region = worldData.cache.getRegion(x shr 9, z shr 9) ?: return AIR
                prevCached = region
                cached = region
            }

            val type = cached.getBlock(x and 511, adjustedY + world.dimensionType().minY(), z and 511)
            return type ?: AIR
        }

        fun isLoaded(
            x: Int,
            z: Int,
        ): Boolean {
            var prevChunk = prev
            if (prevChunk != null && prevChunk.pos.x == x shr 4 && prevChunk.pos.z == z shr 4) {
                return true
            }

            prevChunk = provider.getChunk(x shr 4, z shr 4, ChunkStatus.FULL, false)
            if (prevChunk != null && !prevChunk.isEmpty) {
                prev = prevChunk
                return true
            }

            var prevRegion = prevCached
            if (prevRegion != null && prevRegion.x == x shr 9 && prevRegion.z == z shr 9) {
                return prevRegion.isCached(x and 511, z and 511)
            }

            if (worldData == null) {
                return false
            }

            prevRegion = worldData.cache.getRegion(x shr 9, z shr 9) ?: return false
            prevCached = prevRegion
            return prevRegion.isCached(x and 511, z and 511)
        }

        companion object {
            private val AIR: BlockState = Blocks.AIR.defaultBlockState()

            @JvmStatic
            fun getBlock(
                ctx: IPlayerContext,
                pos: BlockPos,
            ): Block {
                // Won't be called from the pathing thread because the pathing thread doesn't make a single blockpos pog
                return get(ctx, pos).block
            }

            @JvmStatic
            fun get(
                ctx: IPlayerContext,
                pos: BlockPos,
            ): BlockState {
                // Immense iq
                // Can't just do world().get because that doesn't work for out of bounds
                // and toBreak and stuff fails when the movement is instantiated out of load range, but it's
                // not able to BlockStateInterface.get what it's going to walk on
                return BlockStateInterface(ctx).get0(pos.x, pos.y, pos.z)
            }

            // Get the block at x,y,z from this chunk WITHOUT creating a single blockpos object
            @JvmStatic
            fun getFromChunk(
                chunk: LevelChunk,
                x: Int,
                y: Int,
                z: Int,
            ): BlockState {
                val section = chunk.sections[y shr 4]
                if (section.hasOnlyAir()) {
                    return AIR
                }
                return section.getBlockState(x and 15, y and 15, z and 15)
            }
        }
    }
