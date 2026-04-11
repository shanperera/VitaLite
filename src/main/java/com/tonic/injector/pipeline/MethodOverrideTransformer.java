package com.tonic.injector.pipeline;

import com.tonic.injector.annotations.MethodOverride;
import com.tonic.injector.util.AnnotationUtil;
import com.tonic.injector.util.TransformerUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Completely replaces method implementations with bytecode from mixin methods.
 */
public class MethodOverrideTransformer {
    /**
     * Replaces target method with mixin method implementation.
     * @param mixin mixin class containing replacement method
     * @param method method annotated with MethodOverride
     */
    public static void patch(ClassNode mixin, MethodNode method) {
        String name = AnnotationUtil.getAnnotation(method, MethodOverride.class, "value");
        MethodNode toReplace = TransformerUtil.getTargetMethod(mixin, name);

        if (toReplace == null) {
            System.err.println("[MethodOverrideTransformer] Target method not found: " + name + " in mixin " + mixin.name);
            return;
        }

        toReplace.instructions.clear();
        toReplace.tryCatchBlocks.clear();
        toReplace.localVariables.clear();

        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }

        for (AbstractInsnNode insn : method.instructions.toArray()) {
            AbstractInsnNode cloned = insn.clone(labelMap);
            if (cloned != null) {
                toReplace.instructions.add(cloned);
            } else {
                System.err.println("[MethodOverrideTransformer] Warning: Failed to clone instruction: " + insn);
            }
        }

        // Rewrite mixin class references to target class in copied instructions
        ClassNode gamepackClass = TransformerUtil.getBaseClass(mixin);
        if (gamepackClass != null) {
            String mixinName = mixin.name;
            String gamepackName = gamepackClass.name;
            for (AbstractInsnNode insn : toReplace.instructions) {
                if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if (fin.owner.equals(mixinName)) {
                        fin.owner = gamepackName;
                    }
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if (min.owner.equals(mixinName)) {
                        min.owner = gamepackName;
                    }
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode tin = (TypeInsnNode) insn;
                    if (tin.desc.equals(mixinName)) {
                        tin.desc = gamepackName;
                    }
                }
            }
        }

        toReplace.maxStack  = method.maxStack;
        // Ensure maxLocals accounts for 'this' when target is an instance method
        boolean isTargetStatic = (toReplace.access & Opcodes.ACC_STATIC) != 0;
        toReplace.maxLocals = Math.max(method.maxLocals, isTargetStatic ? 0 : 1);
    }
}