# External LOD renderer API 5

This branch provides Voxy's Minecraft 26.2 Vulkan renderer and a CPU-facing
interface for external renderers. The matching
[Caustica integration](https://github.com/skayla2007/Caustica/tree/codex/lodfix6-10000)
consumes this API and ray traces the exported meshes alongside real chunks.
Without an active external renderer, standalone Voxy raster rendering remains
available. Upstream author attribution and the existing license are unchanged.

## Entry points and lifecycle

`me.cortex.voxy.api.VoxyLodApi.VERSION` is a runtime field with value 5.
Consumers must check the installed provider version rather than mix API versions.

Call `registerExternalRenderer(BooleanSupplier)` on the Minecraft render thread
before world creation. Its predicate reports whether the external renderer is
active. An active consumer suppresses Voxy raster allocations and drawing while
keeping model baking, data ingestion and CPU mesh generation available.

Call `update(cameraX, cameraY, cameraZ, projectionView, width, height)` on the
render thread. Use the display resolution for projected detail selection, even
when the consumer traces at a lower internal resolution. The call returns `null`
if a Vulkan world provider is unavailable, or the latest complete `LodScene`.
Selection runs on a worker with one replaceable pending camera request and
coalesces changes at approximately 100 ms intervals. Consumers must tolerate
an initially empty scene and short selection latency during motion.

## Exported data

| Type | Contents |
|---|---|
| `LodScene` | Session epoch, scene revision, Vulkan atlas view, face texture size, selected meshes, fallback ancestors, model palette and colours |
| `LodMesh` | Section key/revision, LOD level, section coordinates, bounds, child-existence mask, immutable packed quads and per-bucket offsets |
| `LodModel` | Six baked face records, flags, tint and corresponding block state |
| `LodQuad` | Decoding of Voxy's packed quad material, position, orientation and extent |
| `LodHierarchy` | Non-overlapping ready coverage selection, complete-branch retention and ancestor coverage queries |

Meshes retain CPU data after the next update. Quad buffers are read-only; offset
and colour arrays are copied on access. Section voxel scale is `1 << level`, and
the section spans `32 << level` blocks. Consumers should use the exported quad
decoders and model face data rather than infer shape from material IDs.

The node index in a scene may be shared, but `LodHierarchy.prepare(scene)` gives
each consumer its own mutable readiness/covered-parent state. Coverage snapshots
include empty leaves so an empty region is not treated as missing geometry.
Previously complete fine coverage can remain while requested replacements load.
Consumers decide when their corresponding GPU meshes are actually ready.

Treat `epoch` changes as a new provider session and invalidate matching consumer
resources. Mesh revisions identify regenerated geometry. Track `atlasView`
separately: atlas growth can change the native view without a new CPU selection.
The atlas is provider-owned; consumers must not destroy it. Atlas extent can
grow, so normalize face texel coordinates using the actual image extent.

GPU export state is captured on the render thread for the CPU selection worker.
Native atlas handles are not worker-owned resources. `deviceHostLock()` supplies
the shared host lock used by the integration for device-idle and queue-submission
coordination; normal Vulkan resource-lifetime and reader-completion rules still
apply when a consumer replaces texture bindings or retires acceleration data.

## Detail, distance and caches

`config/voxy-config.json` controls `section_render_distance` and
`sub_division_size`. The external selection radius is
`section_render_distance * 512` blocks horizontally; a value of 32 selects a
16384-block radius. Projected pixel area determines refinement, matching Voxy's
six-face area rule. The real-chunk projection far plane does not clip external
LOD selection. The provider does not impose Caustica's 10000-block ray limit.

Unvisited CPU cache entries have size limits. Active selection is retained, and
a weak-reference pool can reuse immutable meshes still held by consumers after
strong-cache eviction. Real section changes invalidate retained meshes and
produce updated geometry. Request/in-flight limits regulate loading work rather
than cap the eventual number or precision of selected meshes.

## Build and tests

Use JDK 25 and run `./gradlew build` (`./gradlew.bat build` on Windows). Output
is in `build/libs`. Build this repository before building Caustica, with the two
checkouts named `voxy` and `Caustica` in the same parent directory. Keep only one
matching non-sources Voxy JAR in `build/libs` for Caustica's compile dependency.

The provider has 24 tests covering quad decoding, coverage selection, independent
consumer state, retained mesh invalidation, worker request coalescing/failure and
atlas capacity. `-Dcaustica.voxy.profile=true` enables provider timing/queue logs
used by the paired renderer. Test drivers, worlds, machine-specific logs and SDKs
are local development inputs, not tracked source files.
