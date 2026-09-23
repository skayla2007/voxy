#define SENTINAL_OUT_OF_BOUNDS uint(-1)

#ifdef VOXY_VULKAN
layout(push_constant) uniform PushConstants { uint queueIdx; };
#else
layout(location = NODE_QUEUE_INDEX_BINDING) uniform uint queueIdx;
#endif

layout(binding = NODE_QUEUE_META_BINDING, std430) restrict buffer NodeQueueMeta {
    uvec4 nodeQueueMetadata[MAX_ITERATIONS];
};

layout(binding = NODE_QUEUE_SOURCE_BINDING, std430) restrict readonly buffer NodeQueueSource {
    uint[] nodeQueueSource;
};

layout(binding = NODE_QUEUE_SINK_BINDING, std430) restrict writeonly buffer NodeQueueSink {
    uint[] nodeQueueSink;
};

uint getCurrentNode() {
    if (nodeQueueMetadata[queueIdx].w <= gl_GlobalInvocationID.x) {
        return SENTINAL_OUT_OF_BOUNDS;
    }
    return nodeQueueSource[gl_GlobalInvocationID.x];
}


uint nodePushIndex = -1;
bool pushNodesInit(uint nodeCount) {
    //Debug
    #ifdef DEBUG
    if (queueIdx >= (MAX_ITERATIONS-1)) {
        printf("LOG: Traversal tried inserting a node into next iteration, which is outside max iteration bounds. GID: %d, count: %d", gl_GlobalInvocationID.x, nodeCount);
        nodePushIndex = -1;
        return false;
    }
    #endif

    // Reserve the whole child run without ever letting the GPU write beyond
    // the real scratch-buffer capacity. High-resolution terrain-heavy views
    // can otherwise overflow this queue and lose/corrupt arbitrary LOD tiles.
    uint capacity = uint(nodeQueueSink.length());
    #ifdef VOXY_VULKAN
    uint index = atomicAdd(nodeQueueMetadata[queueIdx+1].w, nodeCount);
    if (index > capacity || nodeCount > capacity-index) {
        // A complete node tree has at most one queue entry per resident node;
        // the Vulkan queue is sized to that ceiling. Keep a defensive fallback
        // for corrupted graphs rather than writing outside the buffer.
        nodePushIndex = -1;
        return false;
    }
    #else
    uint index = atomicAdd(nodeQueueMetadata[queueIdx+1].w, 0);
    for (;;) {
        if (index > capacity || nodeCount > capacity-index) {
            nodePushIndex = -1;
            return false;
        }
        uint previous = atomicCompSwap(nodeQueueMetadata[queueIdx+1].w, index, index+nodeCount);
        if (previous == index) break;
        index = previous;
    }
    #endif
    // Increment the indirect dispatch group count only when this reservation
    // crosses a workgroup boundary.
    // The dispatch count is ceil(queue length / workgroup size). Reserve only
    // the groups actually crossed by this child run. Using LOCAL_SIZE instead
    // of nodeCount added a group for almost every parent node, launching many
    // empty workgroups at dense LOD levels.
    #ifdef VOXY_VULKAN
    uint inc = ((index+nodeCount+LOCAL_SIZE-1)>>LOCAL_SIZE_BITS)
             - ((index+LOCAL_SIZE-1)>>LOCAL_SIZE_BITS);
    if (inc != 0) atomicAdd(nodeQueueMetadata[queueIdx+1].x, inc);
    #else
    uint inc = ((index+LOCAL_SIZE)>>LOCAL_SIZE_BITS)-(index>>LOCAL_SIZE_BITS);
    atomicAdd(nodeQueueMetadata[queueIdx+1].x, inc);
    #endif
    nodePushIndex = index;
    return true;
}

void pushNode(uint nodeId) {
    #ifdef DEBUG
    if (nodePushIndex == -1) {
        printf("LOG: Tried pushing node when push node wasnt successful. GID: %d, pushing: %d", gl_GlobalInvocationID.x, nodeId);
        return;
    }
    #endif
    nodeQueueSink[nodePushIndex++] = nodeId;
}

#define SIMPLE_QUEUE(type, name, bindingIndex) layout(binding = bindingIndex, std430) restrict buffer name##Struct { \
    type name##Index; \
    type##[] name; \
};
