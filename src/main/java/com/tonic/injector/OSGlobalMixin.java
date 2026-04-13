package com.tonic.injector;

import com.tonic.Static;
import com.tonic.util.asm.BytecodeBuilder;
import com.tonic.injector.util.LdcRewriter;
import com.tonic.util.MappingProvider;
import com.tonic.injector.util.expreditor.impls.*;
import com.tonic.model.ConditionType;
import com.tonic.util.dto.JClass;
import com.tonic.util.dto.JField;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class OSGlobalMixin
{
    private static final PathsGetReplacer pathsGetReplacer = new PathsGetReplacer();
    private static final ModifyResourceLoading modifyResourceLoading = new ModifyResourceLoading();
    private static final ReplaceMethodByString replaceMethodByString = new ReplaceMethodByString("Attempted to load patches of already loading midiplayer!");
    private static final RuntimeMaxMemoryReplacer memoryReplacer = new RuntimeMaxMemoryReplacer(805_306_368L);
    private static final SystemPropertyReplacer propertyReplacer = new SystemPropertyReplacer();
    private static final IntegerLiteralReplacer integerReplacer = new IntegerLiteralReplacer(-1094877034);

    public static void patch(ClassNode classNode)
    {
        pathsGetReplacer.instrument(classNode);
        memoryReplacer.instrument(classNode);
        propertyReplacer.instrument(classNode);
        integerReplacer.instrument(classNode);

        if(Static.getCliArgs().isNoMusic() || Static.getCliArgs().isMin())
        {
            replaceMethodByString.instrument(classNode);
            modifyResourceLoading.instrument(classNode);
        }

        for(MethodNode method : classNode.methods)
        {
            randomDat(classNode, method);
            mouseFlag(method);
            isHidden(classNode, method);

            if(!Static.getCliArgs().isIncognito())
            {
                LdcRewriter.rewriteString(
                        method,
                        "Welcome to RuneScape",
                        "<col=FFFFFF>Welcome to </col><col=00FFFF>VitaLite</col>"
                );
            }
        }
    }

    /**
     * Hack to child-proof side effects of headless mode
     */
    public static void isHidden(ClassNode cn, MethodNode method)
    {
        if(!method.name.equals("isHidden") || !method.desc.equals("()Z"))
            return;

        InsnList code = BytecodeBuilder.create()
                .ifBlock(
                        ConditionType.FALSE,  // If not equal to 0 (i.e., if true)
                        b -> b.invokeStatic("com/tonic/Static", "isHeadless", "()Z"),
                        b -> b.pushThis()
                                .invokeVirtual(cn.name, "isSelfHidden", "()Z")
                                .returnValue(Opcodes.IRETURN)
                ).build();

        method.instructions.insert(code);
    }

    public static void mouseFlag(MethodNode method)
    {
        JClass client = MappingProvider.getClass("Client");
        JField mouseFlag = MappingProvider.getField(client, "mouseFlag");

        List<FieldInsnNode> toReplace = new ArrayList<>();

        for(AbstractInsnNode insn : method.instructions)
        {
            if(insn.getOpcode() != Opcodes.GETSTATIC)
                continue;

            FieldInsnNode fin = (FieldInsnNode) insn;
            if(!fin.owner.equals(mouseFlag.getOwnerObfuscatedName()) || !fin.name.equals(mouseFlag.getObfuscatedName()))
                continue;

            toReplace.add(fin);
        }

        if(toReplace.isEmpty())
            return;

        for (FieldInsnNode insn : toReplace) {
            InsnNode iconst0 = new InsnNode(Opcodes.ICONST_0);
            method.instructions.set(insn, iconst0);
        }
    }

    /**
     * Injects setRandomDat call immediately after randomDat object creation.
     * Matches: invokespecial SomeClass."&lt;init&gt;" followed by putstatic client.oc (randomDat)
     */
    public static void randomDat(ClassNode clazz, MethodNode method)
    {
        JClass client = MappingProvider.getClass("Client");
        JField randomDat = MappingProvider.getField(client, "randomDat");
        if(randomDat == null)
        {
            System.out.println("[RandomDat] WARNING: Could not find randomDat field mapping");
            return;
        }

        List<FieldInsnNode> targets = new ArrayList<>();

        for(AbstractInsnNode insn : method.instructions)
        {
            if(insn.getOpcode() != Opcodes.PUTSTATIC)
                continue;

            FieldInsnNode fin = (FieldInsnNode) insn;
            if(!fin.owner.equals(randomDat.getOwnerObfuscatedName()) || !fin.name.equals(randomDat.getObfuscatedName()))
                continue;

            // Check if preceded by a constructor call (new uy/uw/etc <init>)
            AbstractInsnNode prev = insn.getPrevious();
            while(prev != null && prev.getOpcode() == -1)
                prev = prev.getPrevious();

            if(prev == null || prev.getOpcode() != Opcodes.INVOKESPECIAL)
                continue;

            MethodInsnNode ctor = (MethodInsnNode) prev;
            if(!ctor.name.equals("<init>"))
                continue;

            targets.add(fin);
        }

        if(targets.isEmpty())
            return;

        boolean methodExists = clazz.methods.stream()
                .anyMatch(m -> m.name.equals("setRandomDat") && m.desc.equals("(Ljava/lang/String;)V"));

        for(FieldInsnNode target : targets)
        {
            System.out.println("[RandomDat] Injecting setRandomDat AFTER creation site in " + clazz.name + "." + method.name + method.desc
                    + " (setRandomDat exists on class: " + methodExists + ")");

            InsnList code = new InsnList();
            code.add(new LdcInsnNode(clazz.name + "." + method.name + method.desc));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "client", "setRandomDat", "(Ljava/lang/String;)V", false));

            method.instructions.insert(target, code);
        }
    }
}
