package com.tonic.mixins;

import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.injector.annotations.*;
import com.tonic.model.RandomDat;
import com.tonic.util.ReflectBuilder;
import net.runelite.api.Client;

import java.nio.ByteBuffer;

@Mixin("Client")
public abstract class TRandomDatMixin
{
    @Shadow("client")
    public static Client client;

    @Shadow("JX_CHARACTER_ID")
    public static String characterId;

    @Shadow("randomDat")
    public static Object randomDat;

    @Inject
    public static void setRandomDat(String caller)
    {
        System.out.println("[RandomDat] setRandomDat called from: " + caller);
        try
        {
            if (Static.getVitaConfig() == null)
            {
                System.out.println("[RandomDat] VitaConfig not yet initialized, skipping");
                return;
            }

            if (!Static.getVitaConfig().shouldCacheRandomDat())
            {
                System.out.println("[RandomDat] shouldCacheRandomDat is disabled, skipping");
                return;
            }

            String username = ReflectBuilder.of(client)
                    .method("getUsername", null, null)
                    .get();

            String identifier = username != null && !username.isEmpty() ? username : characterId;
            System.out.println("[RandomDat] Identifier: " + identifier);

            byte[] data = RandomDat.getCachedRandomDatData(identifier);
            int[] values = new int[8];

            if (data != null && data.length >= 32)
            {
                ByteBuffer buf = ByteBuffer.wrap(data);
                for (int i = 0; i < 8; i++)
                {
                    values[i] = buf.getInt();
                }
                Logger.info("Using cached random.dat data for user " + identifier);
            }
            else
            {
                for (int i = 0; i < 8; i++)
                {
                    values[i] = -1;
                }
                Logger.info("Spoofing random.dat with default values for user " + identifier);
            }

            Object[] args = new Object[8];
            for (int i = 0; i < 8; i++)
            {
                args[i] = values[i];
            }

            Class<?>[] paramTypes = new Class<?>[]{
                    int.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class
            };
            randomDat = ReflectBuilder.newInstance("uy", paramTypes, args).get();
            Logger.info("random.dat override applied successfully");
        }
        catch (Throwable ex)
        {
            System.out.println("[RandomDat] Error from caller: " + caller + " - " + ex.getClass().getName() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
