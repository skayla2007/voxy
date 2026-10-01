package me.cortex.voxy.api;

import net.minecraft.world.level.block.state.BlockState;

/** Immutable baked model metadata; the texture is in the provider's six-face atlas. */
public record LodModel(int id, int down, int up, int north, int south, int west, int east,
                       int flags, int tint, BlockState state) {
    public int face(int face) {
        return switch (face) {
            case 0 -> down;
            case 1 -> up;
            case 2 -> north;
            case 3 -> south;
            case 4 -> west;
            case 5 -> east;
            default -> throw new IllegalArgumentException("Face outside 0..5");
        };
    }
}
