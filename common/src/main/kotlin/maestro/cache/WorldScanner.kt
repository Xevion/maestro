package maestro.cache

import maestro.api.cache.IWorldScanner
import maestro.api.utils.BlockOptionalMetaLookup
import maestro.api.utils.IPlayerContext
import net.minecraft.client.multiplayer.ClientChunkCache
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.stream.IntStream
import kotlin.math.abs

object WorldScanner : IWorldScanner {
    override fun scanChunkRadius(
        ctx: IPlayerContext,
        filter: BlockOptionalMetaLookup,
        max: Int,
        yLevelThreshold: Int,
        maxSearchRadius: Int,
    ): List<BlockPos> {
        val res = ArrayList<BlockPos>()

        if (filter.blocks().isEmpty()) {
            return res
        }

        val chunkProvider = ctx.world().chunkSource as ClientChunkCache
        val maxSearchRadiusSq = maxSearchRadius * maxSearchRadius
        val playerChunkX = ctx.playerFeet().x shr 4
        val playerChunkZ = ctx.playerFeet().z shr 4
        val playerY = ctx.playerFeet().y - ctx.world().dimensionType().minY()

        val playerYBlockStateContainerIndex = playerY shr 4
        val coordinateIterationOrder =
            IntStream
                .range(0, ctx.world().dimensionType().height() / 16)
                .boxed()
                .sorted(
                    compareBy { y -> abs(y - playerYBlockStateContainerIndex) },
                ).mapToInt { it }
                .toArray()

        var searchRadiusSq = 0
        var foundWithinY = false

        while (true) {
            var allUnloaded = true
            var foundChunks = false

            for (xoff in -searchRadiusSq..searchRadiusSq) {
                for (zoff in -searchRadiusSq..searchRadiusSq) {
                    val distance = xoff * xoff + zoff * zoff
                    if (distance != searchRadiusSq) {
                        continue
                    }

                    foundChunks = true
                    val chunkX = xoff + playerChunkX
                    val chunkZ = zoff + playerChunkZ
                    val chunk = chunkProvider.getChunk(chunkX, chunkZ, null, false) ?: continue

                    allUnloaded = false
                    if (
                        scanChunkInto(
                            chunkX shl 4,
                            chunkZ shl 4,
                            ctx.world().dimensionType().minY(),
                            chunk,
                            filter,
                            res,
                            max,
                            yLevelThreshold,
                            playerY,
                            coordinateIterationOrder,
                        )
                    ) {
                        foundWithinY = true
                    }
                }
            }

            if ((allUnloaded && foundChunks) ||
                (
                    res.size >= max &&
                        (
                            searchRadiusSq > maxSearchRadiusSq ||
                                (searchRadiusSq > 1 && foundWithinY)
                        )
                )
            ) {
                return res
            }

            searchRadiusSq++
        }
    }

    override fun scanChunk(
        ctx: IPlayerContext,
        filter: BlockOptionalMetaLookup,
        pos: ChunkPos,
        max: Int,
        yLevelThreshold: Int,
    ): List<BlockPos> {
        if (filter.blocks().isEmpty()) {
            return emptyList()
        }

        val chunkProvider = ctx.world().chunkSource as ClientChunkCache
        val chunk = chunkProvider.getChunk(pos.x, pos.z, null, false)
        val playerY = ctx.playerFeet().y

        if (chunk == null || chunk.isEmpty) {
            return emptyList()
        }

        val res = ArrayList<BlockPos>()
        scanChunkInto(
            pos.x shl 4,
            pos.z shl 4,
            ctx.world().dimensionType().minY(),
            chunk,
            filter,
            res,
            max,
            yLevelThreshold,
            playerY,
            IntStream.range(0, ctx.world().dimensionType().height() / 16).toArray(),
        )
        return res
    }

    override fun repack(ctx: IPlayerContext): Int = repack(ctx, 40)

    override fun repack(
        ctx: IPlayerContext,
        range: Int,
    ): Int {
        val chunkProvider = ctx.world().chunkSource
        val cachedWorld = ctx.worldData().cachedWorld

        val playerPos = ctx.playerFeet()
        val playerChunkX = playerPos.x shr 4
        val playerChunkZ = playerPos.z shr 4

        val minX = playerChunkX - range
        val minZ = playerChunkZ - range
        val maxX = playerChunkX + range
        val maxZ = playerChunkZ + range

        var queued = 0
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val chunk = chunkProvider.getChunk(x, z, false)
                if (chunk != null && !chunk.isEmpty) {
                    queued++
                    cachedWorld.queueForPacking(chunk as LevelChunk)
                }
            }
        }

        return queued
    }

    private fun scanChunkInto(
        chunkX: Int,
        chunkZ: Int,
        minY: Int,
        chunk: LevelChunk,
        filter: BlockOptionalMetaLookup,
        result: MutableCollection<BlockPos>,
        max: Int,
        yLevelThreshold: Int,
        playerY: Int,
        coordinateIterationOrder: IntArray,
    ): Boolean {
        val chunkInternalStorageArray = chunk.sections
        var foundWithinY = false

        for (y0 in coordinateIterationOrder) {
            val section = chunkInternalStorageArray[y0]
            if (section == null || section.hasOnlyAir()) {
                continue
            }

            val yReal = y0 shl 4
            val bsc = section.states

            for (yy in 0..<16) {
                for (z in 0..<16) {
                    for (x in 0..<16) {
                        val state: BlockState = bsc.get(x, yy, z)
                        if (filter.has(state)) {
                            val y = yReal or yy

                            if (result.size >= max) {
                                if (abs(y - playerY) < yLevelThreshold) {
                                    foundWithinY = true
                                } else {
                                    if (foundWithinY) {
                                        // have found within Y in this chunk, so don't need to
                                        // consider outside Y
                                        // TODO continue iteration to one more sorted Y coordinate
                                        // block
                                        return true
                                    }
                                }
                            }

                            result.add(BlockPos(chunkX or x, y + minY, chunkZ or z))
                        }
                    }
                }
            }
        }

        return foundWithinY
    }
}
