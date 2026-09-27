package me.cortex.voxy.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

//Mixin config plugin for client.voxy.mixins.json (it must live outside the mixin package).
//Skips compat mixins whose target mod is not installed. Sodium in particular is optional: without it (for example
// when another mod replaces world rendering) voxy runs headless and ingests through vanilla chunk events.
public class VoxyMixinPlugin implements IMixinConfigPlugin {
    private static final String PACKAGE = "me.cortex.voxy.client.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.startsWith(PACKAGE)) {
            return true;
        }
        String relative = mixinClassName.substring(PACKAGE.length());
        var loader = FabricLoader.getInstance();
        if (relative.startsWith("sodium.")) {
            return loader.isModLoaded("sodium");
        }
        if (relative.startsWith("nvidium.")) {
            return loader.isModLoaded("sodium") && loader.isModLoaded("nvidium");
        }
        if (relative.startsWith("iris.")) {
            return loader.isModLoaded("iris");
        }
        if (relative.startsWith("flashback.")) {
            return loader.isModLoaded("flashback");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
