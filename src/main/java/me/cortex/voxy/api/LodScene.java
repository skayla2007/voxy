package me.cortex.voxy.api;

import java.util.List;
import java.util.Map;

/** A non-overlapping selection whose meshes/models remain valid after the next tick.
 * Atlas extent may grow; consumers normalize model texel coordinates using the actual image size. */
public record LodScene(long epoch, long revision, long atlasView, int textureSize,
                       List<LodMesh> meshes, List<LodMesh> ancestors, Map<Integer, LodModel> models, int[] colours, Index index) {
    /** One immutable node index; consumer readiness state remains render-thread owned. */
    public static final class Index {
        private LodHierarchy.Selection value;
        synchronized LodHierarchy.Selection get(LodScene scene) {
            if (value == null) value = new LodHierarchy.Selection(scene);
            return value;
        }
    }
    public LodScene {
        meshes = List.copyOf(meshes);
        ancestors = List.copyOf(ancestors);
        models = Map.copyOf(models);
        colours = colours.clone();
    }
    public LodScene(long epoch, long revision, long atlasView, int textureSize,
                    List<LodMesh> meshes, List<LodMesh> ancestors, Map<Integer,LodModel> models, int[] colours) {
        this(epoch,revision,atlasView,textureSize,meshes,ancestors,models,colours,new Index());
    }
    LodScene withAtlas(long view) { return new LodScene(epoch,revision,view,textureSize,meshes,ancestors,models,colours,index); }
    public LodScene(long epoch, long revision, long atlasView, int textureSize,
                    List<LodMesh> meshes, Map<Integer, LodModel> models, int[] colours) {
        this(epoch, revision, atlasView, textureSize, meshes, List.of(), models, colours);
    }
    @Override public int[] colours() { return colours.clone(); }
    /** Unavailable palette entries use the provider's untinted sentinel. */
    public int colour(int index) { return index >= 0 && index < colours.length ? colours[index] : -1; }
}
