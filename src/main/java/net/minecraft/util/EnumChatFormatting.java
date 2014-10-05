package net.minecraft.util;


import net.canarymod.api.chat.CanaryChatFormatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;


public enum EnumChatFormatting {

    BLACK('0'), //
    DARK_BLUE('1'), //
    DARK_GREEN('2'), //
    DARK_AQUA('3'), //
    DARK_RED('4'), //
    DARK_PURPLE('5'), //
    GOLD('6'), //
    GRAY('7'), //
    DARK_GRAY('8'), //
    BLUE('9'), //
    GREEN('a'), //
    AQUA('b'), //
    RED('c'), //
    LIGHT_PURPLE('d'), //
    YELLOW('e'), //
    WHITE('f'), //
    OBFUSCATED('k', true), //
    BOLD('l', true), //
    STRIKETHROUGH('m', true), //
    UNDERLINE('n', true), //
    ITALIC('o', true), //
    RESET('r');

    private static final Map w = Maps.newHashMap();
    private static final Pattern x = Pattern.compile("(?i)" + String.valueOf('\u00a7') + "[0-9A-FK-OR]");
    private final String y;
    private final char z;
    private final boolean A;
    private final String B;
    private final int C;
    private final CanaryChatFormatting ccf; // CanaryMod

    private static String c(String s0) {
        return s0.toLowerCase().replaceAll("[^a-z]", "");
    }

    private EnumChatFormatting(String s0, char c0, int i0) {
        this(s0, c0, false, i0);
    }

    private EnumChatFormatting(String s0, char c0, boolean flag0) {
        this(s0, c0, flag0, -1);
    }

    private EnumChatFormatting(String s0, char c0, boolean flag0, int i0) {
        this.y = s0;
        this.z = c0;
        this.A = flag0;
        this.C = i0;
        this.B = "\u00a7" + c0;
        this.ccf = new CanaryChatFormatting(this); // CanaryMod: install wrapper
    }

    public int b() {
        return this.C;
    }

    public boolean c() {
        return this.A;
    }

    public boolean d() {
        return !this.A && this != RESET;
    }

    public String e() {
        return this.name().toLowerCase();
    }

    public String toString() {
        return this.B;
    }

    public static EnumChatFormatting b(String s0) {
        return s0 == null ? null : (EnumChatFormatting) w.get(c(s0));
    }

    // CanaryMod
    public CanaryChatFormatting getWrapper() {
        return ccf;
    }

    public static EnumChatFormatting a(int i0) {
        if (i0 < 0) {
            return RESET;
        }
        else {
            EnumChatFormatting[] aenumchatformatting = values();
            int i1 = aenumchatformatting.length;

            for (int i2 = 0; i2 < i1; ++i2) {
                EnumChatFormatting enumchatformatting = aenumchatformatting[i2];

                if (enumchatformatting.b() == i0) {
                    return enumchatformatting;
                }
            }

            return null;
        }
    }

    public static Collection a(boolean flag0, boolean flag1) {
        ArrayList arraylist = Lists.newArrayList();
        EnumChatFormatting[] aenumchatformatting = values();
        int i0 = aenumchatformatting.length;

        for (int i1 = 0; i1 < i0; ++i1) {
            EnumChatFormatting enumchatformatting = aenumchatformatting[i1];

            if ((!enumchatformatting.d() || flag0) && (!enumchatformatting.c() || flag1)) {
                arraylist.add(enumchatformatting.e());
            }
        }

        return arraylist;
    }

    static {
        EnumChatFormatting[] aenumchatformatting = values();
        int i0 = aenumchatformatting.length;

        for (int i1 = 0; i1 < i0; ++i1) {
            EnumChatFormatting enumchatformatting = aenumchatformatting[i1];

            w.put(c(enumchatformatting.y), enumchatformatting);
        }

    }
}
