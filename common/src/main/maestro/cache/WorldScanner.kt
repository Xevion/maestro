package maestro.cache

import io.netty.buffer.Unpooled
import maestro.player.PlayerContext
import maestro.utils.BlockOptionalMetaLookup
import maestro.utils.Loggers
import maestro.utils.accessor.IPalettedContainer
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.GlobalPalette
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.chunk.Palette
import net.minecraft.world.level.chunk.PalettedContainer
import net.minecraft.world.level.chunk.SingleValuePalette
import java.util.stream.Collectors
import java.util.stream.Stream

private val log = Loggers.Cache.get()

object WorldScanner {
    private val PALETTE_REGISTRY_SENTINEL = emptyArray<BlockState>()

    fun scanChunkRadius(
        ctx: PlayerContext,
        filter: BlockOptionalMetaLookup,
        max: Int,
        yLevelThreshold: Int,
        maxSearchRadius: Int,
    ): List<BlockPos> {
        require(maxSearchRadius >= 0) { "chunkRange must be >= 0" }

        return scanChunksInternal(
            ctx,
            filter,
            getChunkRange(ctx.playerFeet().x shr 4, ctx.playerFeet().z shr 4, maxSearchRadius),
            max,
        )
    }

    fun scanChunk(
        ctx: PlayerContext,
        filter: BlockOptionalMetaLookup,
        pos: ChunkPos,
        max: Int,
        yLevelThreshold: Int,
    ): List<BlockPos> {
        var stream = scanChunkInternal(ctx, filter, pos)
        if (max >= 0) {
            stream = stream.limit(max.toLong())
        }
        return stream.collect(Collectors.toList())
    }

    fun repack(ctx: PlayerContext): Int = repack(ctx, 40)

    fun repack(
        ctx: PlayerContext,
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

    /** Generates chunks in spiral order, closest first */
    fun getChunkRange(
        centerX: Int,
        centerZ: Int,
        chunkRadius: Int,
    ): List<ChunkPos> {
        val chunks = ArrayList<ChunkPos>()

        // spiral out
        chunks.add(ChunkPos(centerX, centerZ))
        for (i in 1..<chunkRadius) {
            for (j in 0..i) {
                chunks.add(ChunkPos(centerX - j, centerZ - i))
                if (j != 0) {
                    chunks.add(ChunkPos(centerX + j, centerZ - i))
                    chunks.add(ChunkPos(centerX - j, centerZ + i))
                }
                chunks.add(ChunkPos(centerX + j, centerZ + i))
                if (j != i) {
                    chunks.add(ChunkPos(centerX - i, centerZ - j))
                    chunks.add(ChunkPos(centerX + i, centerZ - j))
                    if (j != 0) {
                        chunks.add(ChunkPos(centerX - i, centerZ + j))
                        chunks.add(ChunkPos(centerX + i, centerZ + j))
                    }
                }
            }
        }

        return chunks
    }

    private fun scanChunksInternal(
        ctx: PlayerContext,
        lookup: BlockOptionalMetaLookup,
        chunkPositions: List<ChunkPos>,
        maxBlocks: Int,
    ): List<BlockPos> {
        try {
            var posStream: Stream<BlockPos> =
                chunkPositions.parallelStream().flatMap { p -> scanChunkInternal(ctx, lookup, p) }

            if (maxBlocks >= 0) {
                // WARNING: this can be expensive if maxBlocks is large...
                // see limit's javadoc
                posStream = posStream.limit(maxBlocks.toLong())
            }

            return posStream.collect(Collectors.toList())
        } catch (e: Exception) {
            log
                .atError()
                .setCause(e)
                .addKeyValue("chunk_count", chunkPositions.size)
                .log("Failed to scan chunks")
            throw e
        }
    }

    private fun scanChunkInternal(
        ctx: PlayerContext,
        lookup: BlockOptionalMetaLookup,
        pos: ChunkPos,
    ): Stream<BlockPos> {
        val chunkProvider = ctx.world().chunkSource

        // if chunk is not loaded, return empty stream
        if (!chunkProvider.hasChunk(pos.x, pos.z)) {
            return Stream.empty()
        }

        val chunk = chunkProvider.getChunk(pos.x, pos.z, false) ?: return Stream.empty()

        val chunkX = pos.x.toLong() shl 4
        val chunkZ = pos.z.toLong() shl 4
        val playerSectionY = (ctx.playerFeet().y - ctx.world().minY) shr 4

        return collectChunkSections(
            lookup,
            chunk,
            chunkX,
            chunkZ,
            playerSectionY,
        ).stream()
    }

    private fun collectChunkSections(
        lookup: BlockOptionalMetaLookup,
        chunk: LevelChunk,
        chunkX: Long,
        chunkZ: Long,
        playerSection: Int,
    ): List<BlockPos> {
        // iterate over sections relative to player
        val blocks = ArrayList<BlockPos>()
        val chunkY = chunk.minY
        val sections = chunk.sections
        val l = sections.size

        var i = playerSection - 1
        var j = playerSection

        while (i >= 0 || j < l) {
            if (j < l) {
                visitSection(lookup, sections[j], blocks, chunkX, chunkY + j * 16, chunkZ)
            }
            if (i >= 0) {
                visitSection(lookup, sections[i], blocks, chunkX, chunkY + i * 16, chunkZ)
            }
            j++
            i--
        }

        return blocks
    }

    private fun visitSection(
        lookup: BlockOptionalMetaLookup,
        section: LevelChunkSection?,
        blocks: MutableList<BlockPos>,
        chunkX: Long,
        sectionY: Int,
        chunkZ: Long,
    ) {
        if (section == null || section.hasOnlyAir()) {
            return
        }

        val sectionContainer: PalettedContainer<BlockState> = section.states

        // this won't work if the PaletteStorage is of the type EmptyPaletteStorage
        @Suppress("UNCHECKED_CAST")
        if ((sectionContainer as IPalettedContainer<BlockState>).storage == null) {
            return
        }

        @Suppress("UNCHECKED_CAST")
        val palette: Palette<BlockState> = (sectionContainer as IPalettedContainer<BlockState>).palette

        if (palette is SingleValuePalette) {
            // single value palette doesn't have any data
            if (lookup.has(palette.valueFor(0))) {
                // TODO this is 4k hits, maybe don't return all of them?
                for (x in 0..<16) {
                    for (y in 0..<16) {
                        for (z in 0..<16) {
                            blocks.add(BlockPos(chunkX.toInt() + x, sectionY + y, chunkZ.toInt() + z))
                        }
                    }
                }
            }
            return
        }

        val isInFilter = getIncludedFilterIndices(lookup, palette)
        if (isInFilter.isEmpty()) {
            return
        }

        @Suppress("UNCHECKED_CAST")
        val array = (section.states as IPalettedContainer<BlockState>).storage
        val longArray = array.raw
        val arraySize = array.size
        val bitsPerEntry = array.bits
        val maxEntryValue = (1L shl bitsPerEntry) - 1L

        var i = 0
        var idx = 0

        while (i < longArray.size && idx < arraySize) {
            val l = longArray[i]
            var offset = 0

            while (offset <= 64 - bitsPerEntry && idx < arraySize) {
                val value = ((l shr offset) and maxEntryValue).toInt()
                if (isInFilter[value]) {
                    blocks.add(
                        BlockPos(
                            chunkX.toInt() + ((idx and 255) and 15),
                            sectionY + (idx shr 8),
                            chunkZ.toInt() + ((idx and 255) shr 4),
                        ),
                    )
                }

                offset += bitsPerEntry
                idx++
            }

            i++
        }
    }

    private fun getIncludedFilterIndices(
        lookup: BlockOptionalMetaLookup,
        palette: Palette<BlockState>,
    ): BooleanArray {
        var commonBlockFound = false
        val paletteMap = getPalette(palette)

        if (paletteMap === PALETTE_REGISTRY_SENTINEL) {
            return getIncludedFilterIndicesFromRegistry(lookup)
        }

        val size = paletteMap.size
        val isInFilter = BooleanArray(size)

        for (i in 0..<size) {
            val state = paletteMap[i]
            if (lookup.has(state)) {
                isInFilter[i] = true
                commonBlockFound = true
            } else {
                isInFilter[i] = false
            }
        }

        if (!commonBlockFound) {
            return BooleanArray(0)
        }

        return isInFilter
    }

    private fun getIncludedFilterIndicesFromRegistry(lookup: BlockOptionalMetaLookup): BooleanArray {
        val isInFilter = BooleanArray(Block.BLOCK_STATE_REGISTRY.size())

        for (bom in lookup.blocks()) {
            for (state in bom.allBlockStates) {
                isInFilter[Block.BLOCK_STATE_REGISTRY.getId(state)] = true
            }
        }

        return isInFilter
    }

    /** Cheats to get the actual map of id -> blockstate from the various palette implementations */
    private fun getPalette(palette: Palette<BlockState>): Array<BlockState> {
        if (palette is GlobalPalette) {
            // copying the entire registry is not nice so we treat it as a special case
            return PALETTE_REGISTRY_SENTINEL
        }

        val buf = FriendlyByteBuf(Unpooled.buffer())
        palette.write(buf)
        val size = buf.readVarInt()
        val states = arrayOfNulls<BlockState>(size)

        for (i in 0..<size) {
            val state = Block.BLOCK_STATE_REGISTRY.byId(buf.readVarInt())
            checkNotNull(state) { "BlockState is null at index $i" }
            states[i] = state
        }

        @Suppress("UNCHECKED_CAST")
        return states as Array<BlockState>
    }
}
