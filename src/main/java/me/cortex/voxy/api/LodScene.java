package me.cortex.voxy.api;

import java.util.List;
import java.util.Map;

/** A non-overlapping Voxy LOD selection. Mesh and model objects remain valid after the next tick. */
public record LodScene(long epoch, long revision, long atlasView, int textureSize,
                       List<LodMesh> meshes, List<LodMesh> ancestors, Map<Integer, LodModel> models, int[] colours) {
    public LodScene {
        meshes = List.copyOf(meshes);
        ancestors = List.copyOf(ancestors);
        models = Map.copyOf(models);
        colours = colours.clone();
    }
    public LodScene(long epoch, long revision, long atlasView, int textureSize,
                    List<LodMesh> meshes, Map<Integer, LodModel> models, int[] colours) {
        this(epoch, revision, atlasView, textureSize, meshes, List.of(), models, colours);
    }
    @Override public int[] colours() { return colours.clone(); }
    /** Unavailable palette entries use the provider's untinted sentinel. */
    public int colour(int index) { return index >= 0 && index < colours.length ? colours[index] : -1; }
}
