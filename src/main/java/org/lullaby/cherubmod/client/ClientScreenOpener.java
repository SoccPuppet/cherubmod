package org.lullaby.cherubmod.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lullaby.cherubmod.client.gui.TarotDeckScreen;

public class ClientScreenOpener {
    public static void openTarotDeckScreen() {
        Minecraft.getInstance().setScreen(new TarotDeckScreen());
    }
}