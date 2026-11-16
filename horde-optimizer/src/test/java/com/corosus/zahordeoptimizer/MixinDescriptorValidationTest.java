package com.corosus.zahordeoptimizer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies every mixin in za_horde_optimizer.mixins.json against the actual Minecraft 26.3
 * and ZombieAwareness bytecode on the classpath.
 */
public class MixinDescriptorValidationTest {

    @Test
    void allMixinTargetsShadowsInvokersAndRedirectsMatchBytecode() throws Exception {
        ClassLoader cl = MixinDescriptorValidationTest.class.getClassLoader();
        try (InputStream is = cl.getResourceAsStream("za_horde_optimizer.mixins.json")) {
            assertNotNull(is, "za_horde_optimizer.mixins.json must be on classpath");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = root.get("package").getAsString();
            JsonArray mixins = root.getAsJsonArray("mixins");
            assertNotNull(mixins);
            assertTrue(mixins.size() > 0, "Expected at least one mixin");

            for (JsonElement elem : mixins) {
                String mixinClassName = pkg + "." + elem.getAsString();
                ClassNode mixinNode = readClassNode(cl, mixinClassName.replace('.', '/') + ".class");
                String targetInternalName = extractMixinTarget(mixinNode);
                assertNotNull(targetInternalName, "Missing @Mixin target on " + mixinClassName);

                ClassNode targetNode = readClassNode(cl, targetInternalName + ".class");
                validateMixinMembers(mixinClassName, mixinNode, targetNode);
            }
        }
    }

    @Test
    void newZombieAwarenessWardenAndRampageSkeletonMixinsMatchBytecode() throws Exception {
        ClassLoader cl = MixinDescriptorValidationTest.class.getClassLoader();
        List<String> zaMixins = List.of(
                "com.corosus.zombieawareness.mixin.MixinWarden",
                "com.corosus.zombieawareness.mixin.MixinAbstractSkeleton",
                "com.corosus.zombieawareness.mixin.MixinRangedBowAttackGoal"
        );
        for (String mixinClassName : zaMixins) {
            ClassNode mixinNode = readClassNode(cl, mixinClassName.replace('.', '/') + ".class");
            String targetInternalName = extractMixinTarget(mixinNode);
            assertNotNull(targetInternalName, "Missing @Mixin target on " + mixinClassName);

            ClassNode targetNode = readClassNode(cl, targetInternalName + ".class");
            validateMixinMembers(mixinClassName, mixinNode, targetNode);
        }
    }

    private static ClassNode readClassNode(ClassLoader cl, String resourcePath) throws IOException {
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            assertNotNull(is, "Class bytecode not found on classpath: " + resourcePath);
            ClassNode node = new ClassNode();
            new ClassReader(is).accept(node, 0);
            return node;
        }
    }

    private static String extractMixinTarget(ClassNode mixinNode) {
        if (mixinNode.invisibleAnnotations == null) {
            return null;
        }
        for (AnnotationNode ann : mixinNode.invisibleAnnotations) {
            if ("Lorg/spongepowered/asm/mixin/Mixin;".equals(ann.desc) && ann.values != null) {
                for (int i = 0; i < ann.values.size() - 1; i += 2) {
                    if ("value".equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof List<?> list && !list.isEmpty()) {
                        Type type = (Type) list.get(0);
                        return type.getInternalName();
                    }
                }
            }
        }
        return null;
    }

    private static void validateMixinMembers(String mixinName, ClassNode mixinNode, ClassNode targetNode) {
        for (FieldNode field : mixinNode.fields) {
            if (hasInvisibleAnnotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")) {
                boolean found = targetNode.fields.stream()
                        .anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc));
                assertTrue(found, mixinName + " @Shadow field missing on " + targetNode.name + ": " + field.name + " " + field.desc);
            }
        }

        for (MethodNode method : mixinNode.methods) {
            if (method.invisibleAnnotations == null) {
                continue;
            }
            for (AnnotationNode ann : method.invisibleAnnotations) {
                if ("Lorg/spongepowered/asm/mixin/gen/Invoker;".equals(ann.desc)) {
                    String invokerTarget = getAnnotationString(ann, "value");
                    boolean found = targetNode.methods.stream().anyMatch(m -> m.name.equals(invokerTarget));
                    assertTrue(found, mixinName + " @Invoker target missing on " + targetNode.name + ": " + invokerTarget);
                } else if ("Lorg/spongepowered/asm/mixin/injection/Inject;".equals(ann.desc)) {
                    String targetMethod = getFirstStringValue(ann, "method");
                    assertMethodExists(mixinName, targetNode, targetMethod);
                } else if ("Lorg/spongepowered/asm/mixin/injection/Redirect;".equals(ann.desc)
                        || "Lorg/spongepowered/asm/mixin/injection/ModifyArg;".equals(ann.desc)) {
                    String targetMethod = getFirstStringValue(ann, "method");
                    MethodNode targetMethodNode = assertMethodExists(mixinName, targetNode, targetMethod);
                    AnnotationNode atNode = getAnnotationChild(ann, "at");
                    assertNotNull(atNode, mixinName + " injector missing @At");
                    String invokeTarget = getAnnotationString(atNode, "target");
                    assertNotNull(invokeTarget, mixinName + " injector missing @At(target=...)");
                    assertInvokeTargetPresent(mixinName, targetMethodNode, invokeTarget);
                }
            }
        }
    }

    private static MethodNode assertMethodExists(String mixinName, ClassNode targetNode, String methodRef) {
        assertNotNull(methodRef, mixinName + " missing method= on injector");
        int parenIdx = methodRef.indexOf('(');
        String name = parenIdx >= 0 ? methodRef.substring(0, parenIdx) : methodRef;
        String desc = parenIdx >= 0 ? methodRef.substring(parenIdx) : null;

        for (MethodNode m : targetNode.methods) {
            if (m.name.equals(name) && (desc == null || m.desc.equals(desc))) {
                return m;
            }
        }
        fail(mixinName + " target method not found on " + targetNode.name + ": " + methodRef);
        return null;
    }

    private static void assertInvokeTargetPresent(String mixinName, MethodNode targetMethod, String fullTarget) {
        int semiIdx = fullTarget.indexOf(';');
        int parenIdx = fullTarget.indexOf('(');
        assertTrue(semiIdx > 1 && parenIdx > semiIdx, "Invalid @At target descriptor: " + fullTarget);

        String owner = fullTarget.substring(1, semiIdx);
        String name = fullTarget.substring(semiIdx + 1, parenIdx);
        String desc = fullTarget.substring(parenIdx);

        for (AbstractInsnNode insn = targetMethod.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.owner.equals(owner) && minsn.name.equals(name) && minsn.desc.equals(desc)) {
                    return;
                }
            }
        }
        fail(mixinName + " @At(INVOKE) target " + fullTarget + " not found inside " + targetMethod.name + targetMethod.desc);
    }

    private static boolean hasInvisibleAnnotation(List<AnnotationNode> list, String desc) {
        if (list == null) return false;
        return list.stream().anyMatch(a -> desc.equals(a.desc));
    }

    private static String getAnnotationString(AnnotationNode ann, String key) {
        if (ann.values == null) return null;
        for (int i = 0; i < ann.values.size() - 1; i += 2) {
            if (key.equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof String s) {
                return s;
            }
        }
        return null;
    }

    private static String getFirstStringValue(AnnotationNode ann, String key) {
        if (ann.values == null) return null;
        for (int i = 0; i < ann.values.size() - 1; i += 2) {
            if (key.equals(ann.values.get(i))) {
                Object val = ann.values.get(i + 1);
                if (val instanceof String s) return s;
                if (val instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof String s) return s;
            }
        }
        return null;
    }

    private static AnnotationNode getAnnotationChild(AnnotationNode ann, String key) {
        if (ann.values == null) return null;
        for (int i = 0; i < ann.values.size() - 1; i += 2) {
            if (key.equals(ann.values.get(i)) && ann.values.get(i + 1) instanceof AnnotationNode child) {
                return child;
            }
        }
        return null;
    }
}
